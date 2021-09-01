package com.datahub.metadata.authorization;

import com.linkedin.common.Ownership;
import com.linkedin.common.urn.Urn;
import com.linkedin.entity.client.AspectClient;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.identity.GroupMembership;
import com.linkedin.metadata.aspect.CorpUserAspect;
import com.linkedin.metadata.aspect.VersionedAspect;
import com.linkedin.metadata.authorization.PoliciesConfig;
import com.linkedin.metadata.snapshot.CorpUserSnapshot;
import com.linkedin.policy.DataHubActorFilter;
import com.linkedin.policy.DataHubPolicyInfo;
import com.linkedin.policy.DataHubResourceFilter;
import com.linkedin.r2.RemoteInvocationException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import static com.linkedin.metadata.Constants.*;

@Slf4j
public class PolicyEngine {

  private final EntityClient _entityClient;
  private final AspectClient _aspectClient;

  public PolicyEngine(
      final EntityClient entityClient,
      final AspectClient aspectClient) {
    _entityClient = entityClient;
    _aspectClient = aspectClient;
  }

  public PolicyEvaluationResult evaluatePolicy(
      final DataHubPolicyInfo policy,
      final String principal,
      final String privilege,
      final Optional<ResourceSpec> resource) {

    final PolicyEvaluationContext context = new PolicyEvaluationContext();

    // If policy is inactive, simply return DENY.
    if (PoliciesConfig.INACTIVE_POLICY_STATE.equals(policy.getState())) {
      return PolicyEvaluationResult.DENIED;
    }

    // If the privilege is not in scope, deny the request.
    if (!isPrivilegeMatch(privilege, policy.getPrivileges(), context)) {
      return PolicyEvaluationResult.DENIED;
    }

    // If the resource is not in scope, deny the request.
    if (!isResourceMatch(resource, policy.getResources(), context)) {
      return PolicyEvaluationResult.DENIED;
    }

    // If the actor does not match, deny the request.
    if (!isActorMatch(principal, policy.getActors(), resource, context)) {
      return PolicyEvaluationResult.DENIED;
    }

    // All portions of the Policy match. Grant the request.
    return PolicyEvaluationResult.GRANTED;
  }

  /**
   * Returns true if the privilege portion of a DataHub policy matches a the privilege being evaluated, false otherwise.
   */
  private boolean isPrivilegeMatch(
      final String requestPrivilege,
      final List<String> policyPrivileges,
      final PolicyEvaluationContext context) {
    return policyPrivileges.contains(requestPrivilege);
  }

  /**
   * Returns true if the resource portion of a DataHub policy matches a the resource being evaluated, false otherwise.
   */
  private boolean isResourceMatch(
      final Optional<ResourceSpec> requestResource,
      final @Nullable DataHubResourceFilter policyResourceFilter,
      final PolicyEvaluationContext context) {
    if (policyResourceFilter == null) {
      // No resource defined on the policy.
      return true;
    }
    if (!requestResource.isPresent()) {
      // Resource filter present in policy, but no resource spec provided.
      return false;
    }
    final ResourceSpec resourceSpec = requestResource.get();
    final boolean resourceTypesMatch = policyResourceFilter.hasType() && policyResourceFilter.getType().equals(resourceSpec.getType());
    final boolean resourceIdentityMatch =
        policyResourceFilter.isAllResources()
            || (policyResourceFilter.hasResources() && Objects.requireNonNull(policyResourceFilter.getResources())
        .stream()
        .anyMatch(resource -> resource.toString().equals(resourceSpec.getResource())));
    // If the resource's type and identity match, then the resource matches the policy.
    return resourceTypesMatch && resourceIdentityMatch;
  }

  /**
   * Returns true if the actor portion of a DataHub policy matches a the actor being evaluated, false otherwise.
   */
  private boolean isActorMatch(
      final String principal,
      final DataHubActorFilter actorFilter,
      final Optional<ResourceSpec> resourceSpec,
      final PolicyEvaluationContext context) {

    // 1. If the principal is a matching "User" in the actor filter, return true immediately.
     if (isUserMatch(principal, actorFilter)) {
       return true;
     }

    // 2. If the principal is in a matching "Group" in the actor filter, return true immediately.
    if (isGroupMatch(principal, actorFilter, context)) {
      return true;
    }

    // 3. If the principal is the owner, either directly or indirectly via a group, return true
    return isOwnerMatch(principal, actorFilter, resourceSpec, context);
  }

  private boolean isUserMatch(final String principal, final DataHubActorFilter actorFilter) {
    // If the principal is a matching "User" in the actor filter, return true immediately.
    return actorFilter.isAllUsers() || (actorFilter.hasUsers() && Objects.requireNonNull(actorFilter.getUsers())
        .stream()
        .anyMatch(user -> user.toString().equals(principal)));
  }

  private boolean isGroupMatch(final String principal, final DataHubActorFilter actorFilter, final PolicyEvaluationContext context) {
    // If the principal is in a matching "Group" in the actor filter, return true immediately.
    if (actorFilter.isAllGroups() || actorFilter.hasGroups()) {
      final Set<String> groups = resolveGroups(principal, context);
      return actorFilter.isAllGroups() || (actorFilter.hasGroups() && Objects.requireNonNull(actorFilter.getGroups())
          .stream()
          .anyMatch(groupUrn -> groups.contains(groupUrn.toString())));
    }
    // If there are no groups on the policy, return false for the group match.
    return false;
  }

  private boolean isOwnerMatch(
      final String principal,
      final DataHubActorFilter actorFilter,
      final Optional<ResourceSpec> requestResource,
      final PolicyEvaluationContext context) {

    // If the policy does not apply to owners, or there is no resource to own, return false immediately.
    if (!actorFilter.isResourceOwners() || !requestResource.isPresent()) {
      return false;
    }

    // Otherwise, evaluate ownership match.
    final ResourceSpec resourceSpec = requestResource.get();

    try {

      // Fetch the latest version of "ownership" aspect for the resource.
      final VersionedAspect aspect = _aspectClient.getAspect(
          resourceSpec.getResource(),
          OWNERSHIP_ASPECT_NAME,
          ASPECT_LATEST_VERSION,
          SYSTEM_ACTOR);

      final Ownership ownership = aspect.getAspect().getOwnership();

      if (isUserOwner(principal, ownership)) {
        return true;
      }

      final Set<String> groups = resolveGroups(principal, context);
      if (isGroupOwner(groups, ownership)) {
        return true;
      }
    } catch (Exception e) {
      // todo: specifically catch the 404 returned by GMS when ownership does not exist.
      log.error(String.format("Failed to resolve Ownership of resource with URN %s. Returning DENY.", resourceSpec.getResource()), e);
    }
    return false;
  }

  private boolean isUserOwner(String principal, Ownership ownership) {
    return ownership.getOwners().stream().anyMatch(owner -> principal.equals(owner.getOwner().toString()));
  }

  private boolean isGroupOwner(Set<String> groups, Ownership ownership) {
    return ownership.getOwners().stream().anyMatch(owner -> groups.contains(owner.getOwner().toString()));
  }

  private Set<String> resolveGroups(String principal, PolicyEvaluationContext context) {

    if (context.groups != null) {
      return context.groups;
    }

    Set<String> groups = new HashSet<>();
    try {
      Urn urn = Urn.createFromString(principal);
      Optional<GroupMembership> maybeGroups = resolveGroupMembership(urn);
      maybeGroups.ifPresent(groupMembership -> groupMembership.getGroups().forEach(groupUrn -> groups.add(groupUrn.toString())));
      context.setGroups(groups); // Cache the groups.
      return groups;
    } catch (URISyntaxException e) {
      log.error(String.format("Failed to bind principal %s to an URN. Denying the authorization request", principal));
      return Collections.emptySet();
    }
  }

  // TODO: Optimization - Cache the group membership. Refresh periodically.
  private Optional<GroupMembership> resolveGroupMembership(final Urn urn) {
    try {
      final CorpUserSnapshot corpUser = _entityClient.get(urn, SYSTEM_ACTOR).getValue().getCorpUserSnapshot();
      for (CorpUserAspect aspect : corpUser.getAspects()) {
        if (aspect.isGroupMembership()) {
          // Found group membership.
          return Optional.of(aspect.getGroupMembership());
        }
      }

    } catch (RemoteInvocationException e) {
      throw new RuntimeException(String.format("Failed to fetch corpUser for urn %s", urn), e);
    }
    return Optional.empty();
  }

  /**
   * Class used to store state across a single Policy evaluation.
   */
  static class PolicyEvaluationContext {
    private Set<String> groups;
    public void setGroups(Set<String> groups) {
      this.groups = groups;
    }
  }

  /**
   * Class used to represent the result of a Policy evaluation
   */
  static class PolicyEvaluationResult {
    public static final PolicyEvaluationResult GRANTED = new PolicyEvaluationResult(true);
    public static final PolicyEvaluationResult DENIED = new PolicyEvaluationResult(false);

    private final boolean isGranted;

    private PolicyEvaluationResult(boolean isGranted) {
      this.isGranted = isGranted;
    }

    public boolean isGranted() {
      return this.isGranted;
    }
  }
}

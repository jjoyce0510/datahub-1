package com.linkedin.metadata;

/**
 * Static class containing commonly-used constants across DataHub services.
 */
public class Constants {
  public static final String INTERNAL_DELEGATED_FOR_ACTOR_HEADER_NAME = "X-DataHub-Delegated-For";
  public static final String INTERNAL_DELEGATED_FOR_ACTOR_TYPE = "X-DataHub-Delegated-For-";

  public static final String DATAHUB_ACTOR = "urn:li:corpuser:datahub"; // Super user.
  public static final String SYSTEM_ACTOR = "urn:li:corpuser:__datahub_system"; // DataHub internal service principal.
  public static final String UNKNOWN_ACTOR = "urn:li:corpuser:UNKNOWN"; // Unknown principal.
  public static final Long ASPECT_LATEST_VERSION = 0L;

  /**
   * Entities
   */
  public static final String CORP_USER_ENTITY_NAME = "corpuser";
  public static final String CORP_GROUP_ENTITY_NAME = "corpGroup";
  public static final String INGESTION_SOURCE_ENTITY_NAME = "dataHubIngestionSource";
  public static final String SECRETS_ENTITY_NAME = "dataHubSecret";
  public static final String EXECUTION_REQUEST_ENTITY_NAME = "dataHubExecutionRequest";


  /**
   * Aspects
   */
  public static final String OWNERSHIP_ASPECT_NAME = "ownership";
  public static final String INSTITUTIONAL_MEMORY_ASPECT_NAME = "institutionalMemory";
  public static final String CORP_GROUP_INFO_ASPECT_NAME = "corpGroupInfo";
  public static final String CORP_GROUP_KEY_ASPECT_NAME = "corpGroupKey";
  public static final String GROUP_MEMBERSHIP_ASPECT_NAME = "groupMembership";
  public static final String CORP_USER_STATUS_ASPECT_NAME = "corpUserStatus";
  public static final String CORP_USER_KEY_ASPECT_NAME = "corpUserKey";
  public static final String INGESTION_INFO_ASPECT_NAME = "dataHubIngestionSourceInfo";
  public static final String SECRET_VALUE_ASPECT_NAME = "dataHubSecretValue";
  public static final String EXECUTION_REQUEST_INPUT_ASPECT_NAME = "dataHubExecutionRequestInput";
  public static final String EXECUTION_REQUEST_SIGNAL_ASPECT_NAME = "dataHubExecutionRequestSignal";
  public static final String EXECUTION_REQUEST_RESULT_ASPECT_NAME = "dataHubExecutionRequestResult";


  /**
   * User Status
   */
  public static final String CORP_USER_STATUS_ACTIVE = "ACTIVE";

  private Constants() { }
}

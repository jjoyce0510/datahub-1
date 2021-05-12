package com.linkedin.metadata.kafka.config;

import com.linkedin.metadata.dao.internal.BaseRemoteWriterDAO;
import com.linkedin.metadata.dao.internal.RestliRemoteWriterDAO;
import com.linkedin.metadata.restli.DefaultRestliClientFactory;
import com.linkedin.restli.client.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RemoteWriterConfig {

  @Value("${GMS_HOST:localhost}")
  private String gmsHost;
  @Value("${GMS_PORT:8080}")
  private int gmsPort;
  @Value("${GMS_USE_SSL:false}")
  private boolean gmsUseSSL;
  @Value("${GMS_SSL_PROTOCOL:#{null}}")
  private String gmsSslProtocol;

  @Bean
  public BaseRemoteWriterDAO remoteWriterDAO() {
    Client restClient = DefaultRestliClientFactory.getRestLiClient(gmsHost, gmsPort, gmsUseSSL, gmsSslProtocol);
    return new RestliRemoteWriterDAO(restClient);
  }
}

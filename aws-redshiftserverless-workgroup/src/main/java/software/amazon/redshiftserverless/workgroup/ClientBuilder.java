package software.amazon.redshiftserverless.workgroup;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.redshiftarcadiacoral.RedshiftArcadiaCoralClient;

import java.net.URI;

public class ClientBuilder {

  public static RedshiftArcadiaCoralClient getClient() {

    return RedshiftArcadiaCoralClient.builder()
            .endpointOverride(URI.create("https://integ.us-east-1.serverless.redshift.aws.a2z.com"))
            .region(Region.US_EAST_1)
            .build();
  }
}

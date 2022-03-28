package software.amazon.redshiftserverless.workgroup;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.redshiftarcadiacoral.RedshiftArcadiaCoralClient;
// TODO: replace all usage of SdkClient with your service client type, e.g; YourServiceClient
import software.amazon.cloudformation.LambdaWrapper;

import java.net.URI;

public class ClientBuilder {
  /*
  TODO: uncomment the following, replacing YourServiceClient with your service client name
  It is recommended to use static HTTP client so less memory is consumed
  e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/master/aws-logs-loggroup/src/main/java/software/amazon/logs/loggroup/ClientBuilder.java#L9
  */
  public static RedshiftArcadiaCoralClient getClient() {

    return RedshiftArcadiaCoralClient.builder()
            .endpointOverride(URI.create("https://devo.us-east-1.serverless.redshift.aws.a2z.com"))
            .region(Region.US_EAST_1)
            .build();
  }
}

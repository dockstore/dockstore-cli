package io.dockstore.support;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import java.util.Map;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackInstanceResponse;
import software.amazon.awssdk.services.cloudformation.model.StackInstance;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.model.ListDiscoveredResourcesRequest;
import software.amazon.awssdk.services.config.model.ListDiscoveredResourcesResponse;
import software.amazon.awssdk.services.config.model.ResourceIdentifier;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * Configure a credentials file like so https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html before running
 * This assumes you have permissions to access the relevant AWS APIs
 */
public final class Inventory {

    private Inventory() {

    }

    public static void main(String[] args) {

        String[] resourceTypes = { "AWS::ACM::Certificate", "AWS::ApiGateway::RestApi", "AWS::CloudFormation::Stack",
            "AWS::CloudFront::Distribution", "AWS::CloudWatch::Alarm", "AWS::EC2::Instance", "AWS::EC2::SecurityGroup",
            "AWS::EC2::Subnet", "AWS::EC2::VPC", "AWS::ElasticLoadBalancingV2::LoadBalancer", "AWS::IAM::Group", "AWS::IAM::User",
            "AWS::Lambda::Function", "AWS::RDS::DBInstance", "AWS::S3::Bucket", "AWS::SQS::Queue", "AWS::WAF::WebACL" };
        ConfigClient build = ConfigClient.builder().build();
        Table<String, String, String> outputTable = TreeBasedTable.create();
        for (String type : resourceTypes) {
            ListDiscoveredResourcesRequest build1 = ListDiscoveredResourcesRequest.builder().resourceType(type).build();
            ListDiscoveredResourcesResponse listDiscoveredResourcesResponse = build.listDiscoveredResources(build1);
            for (ResourceIdentifier id : listDiscoveredResourcesResponse.resourceIdentifiers()) {
                Map<String, String> row = outputTable.row(id.resourceId());
                row.put("Type", id.resourceTypeAsString());
                if (id.resourceName() != null) {
                    outputTable.put(id.resourceId(), "Name", id.resourceName());
                }
                processDetailedInfo(id, row);
            }
        }

        for (Table.Cell<String, String, String> cell : outputTable.cellSet()) {
            System.out.println("(" + cell.getRowKey() + "," + cell.getColumnKey() + ")=" + cell.getValue());
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private static void processDetailedInfo(ResourceIdentifier id, Map<String, String> strings) {
        switch (id.resourceType()) {
        case AWS_EC2_CUSTOMER_GATEWAY:
            break;
        case AWS_EC2_EIP:
            break;
        case AWS_EC2_HOST:
            break;
        case AWS_EC2_INSTANCE:
            Ec2Client ec2Client = Ec2Client.builder().build();
            DescribeInstancesRequest.Builder builder = DescribeInstancesRequest.builder().instanceIds(id.resourceId());
            DescribeInstancesResponse describeInstancesResponse = ec2Client.describeInstances(builder.build());
            Instance instance = describeInstancesResponse.reservations().get(0).instances().get(0);
            // demo some extra info for EC2 instances
            strings.put("EC2:ImageID", instance.imageId());
            strings.put("EC2:InstanceType", instance.instanceTypeAsString());
            break;
        case AWS_EC2_INTERNET_GATEWAY:
            break;
        case AWS_EC2_NETWORK_ACL:
            break;
        case AWS_EC2_NETWORK_INTERFACE:
            break;
        case AWS_EC2_ROUTE_TABLE:
            break;
        case AWS_EC2_SECURITY_GROUP:
            break;
        case AWS_EC2_SUBNET:
            break;
        case AWS_CLOUD_TRAIL_TRAIL:
            break;
        case AWS_EC2_VOLUME:
            break;
        case AWS_EC2_VPC:
            break;
        case AWS_EC2_VPN_CONNECTION:
            break;
        case AWS_EC2_VPN_GATEWAY:
            break;
        case AWS_IAM_GROUP:
            break;
        case AWS_IAM_POLICY:
            break;
        case AWS_IAM_ROLE:
            break;
        case AWS_IAM_USER:
            break;
        case AWS_ACM_CERTIFICATE:
            break;
        case AWS_RDS_DB_INSTANCE:
            RdsClient rdsClient = RdsClient.builder().build();
            DescribeDbInstancesRequest.Builder rBuilder = DescribeDbInstancesRequest.builder().dbInstanceIdentifier(id.resourceName());
            DescribeDbInstancesResponse describeDbInstancesResponse = rdsClient.describeDBInstances(rBuilder.build());
            DBInstance dbInstance = describeDbInstancesResponse.dbInstances().get(0);
            // demo some extra info for RDS instances
            strings.put("RDS:engine", dbInstance.engine());
            strings.put("RDS:storageType", dbInstance.storageType());
            break;
        case AWS_RDS_DB_SUBNET_GROUP:
            break;
        case AWS_RDS_DB_SECURITY_GROUP:
            break;
        case AWS_RDS_DB_SNAPSHOT:
            break;
        case AWS_RDS_EVENT_SUBSCRIPTION:
            break;
        case AWS_ELASTIC_LOAD_BALANCING_V2_LOAD_BALANCER:
            break;
        case AWS_S3_BUCKET:
            S3Client s3Client = S3Client.builder().build();
            ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(id.resourceName()).build());
            // demo some extra info for S3 instances
            strings.put("S3:numberObjects", listObjectsV2Response.keyCount().toString());
            break;
        case AWS_SSM_MANAGED_INSTANCE_INVENTORY:
            break;
        case AWS_REDSHIFT_CLUSTER:
            break;
        case AWS_REDSHIFT_CLUSTER_SNAPSHOT:
            break;
        case AWS_REDSHIFT_CLUSTER_PARAMETER_GROUP:
            break;
        case AWS_REDSHIFT_CLUSTER_SECURITY_GROUP:
            break;
        case AWS_REDSHIFT_CLUSTER_SUBNET_GROUP:
            break;
        case AWS_REDSHIFT_EVENT_SUBSCRIPTION:
            break;
        case AWS_CLOUD_WATCH_ALARM:
            break;
        case AWS_CLOUD_FORMATION_STACK:
            // https://github.com/aws/aws-sdk-java-v2/issues/206
            IamClient iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build();
            GetUserResponse user = iamClient.getUser();
            String[] split = user.user().arn().split(":");
            CloudFormationClient build = CloudFormationClient.builder().build();
            DescribeStackInstanceResponse describeStackInstanceResponse = null;
            // doesn't quite work, will need to puzzle out later
            //            for (Region region : Region.regions()) {
            //                // don't know how to figure out proper region
            //                try {
            //                    describeStackInstanceResponse = build.describeStackInstance(
            //                            DescribeStackInstanceRequest.builder().stackInstanceAccount(split[4]).stackInstanceRegion(region.toString()).stackSetName(id.resourceName()).build());
            //                } catch (Exception e) {
            //                    // will die on incorrect region
            //                }
            //            }
            if (describeStackInstanceResponse != null) {
                StackInstance stackInstance = describeStackInstanceResponse.stackInstance();
                /// demo some extra info for cloudformation instances
                strings.put("CloudFormation:status", stackInstance.status().toString());
                strings.put("CloudFormation:statusreason", stackInstance.statusReason());
            }
            break;
        case AWS_DYNAMO_DB_TABLE:
            break;
        case AWS_AUTO_SCALING_AUTO_SCALING_GROUP:
            break;
        case AWS_AUTO_SCALING_LAUNCH_CONFIGURATION:
            break;
        case AWS_AUTO_SCALING_SCALING_POLICY:
            break;
        case AWS_AUTO_SCALING_SCHEDULED_ACTION:
            break;
        case AWS_CODE_BUILD_PROJECT:
            break;
        case AWS_WAF_RATE_BASED_RULE:
            break;
        case AWS_WAF_RULE:
            break;
        case AWS_WAF_WEB_ACL:
            break;
        case AWS_WAF_REGIONAL_RATE_BASED_RULE:
            break;
        case AWS_WAF_REGIONAL_RULE:
            break;
        case AWS_WAF_REGIONAL_WEB_ACL:
            break;
        case AWS_CLOUD_FRONT_DISTRIBUTION:
            break;
        case AWS_CLOUD_FRONT_STREAMING_DISTRIBUTION:
            break;
        case AWS_WAF_RULE_GROUP:
            break;
        case AWS_WAF_REGIONAL_RULE_GROUP:
            break;
        case AWS_LAMBDA_FUNCTION:
            LambdaClient lambdaClient = LambdaClient.builder().build();
            GetFunctionRequest.Builder builder1 = GetFunctionRequest.builder().functionName(id.resourceName());
            GetFunctionResponse function = lambdaClient.getFunction(builder1.build());
            // demo some extra info for lambdas instances
            strings.put("Lambda:codelocation", function.code().location());
            strings.put("Lambda:revisionid", function.configuration().revisionId());
            break;
        case AWS_ELASTIC_BEANSTALK_APPLICATION:
            break;
        case AWS_ELASTIC_BEANSTALK_APPLICATION_VERSION:
            break;
        case AWS_ELASTIC_BEANSTALK_ENVIRONMENT:
            break;
        case AWS_ELASTIC_LOAD_BALANCING_LOAD_BALANCER:
            break;
        case AWS_X_RAY_ENCRYPTION_CONFIG:
            break;
        case AWS_SSM_ASSOCIATION_COMPLIANCE:
            break;
        case AWS_SSM_PATCH_COMPLIANCE:
            break;
        case AWS_SHIELD_PROTECTION:
            break;
        case AWS_SHIELD_REGIONAL_PROTECTION:
            break;
        case AWS_CONFIG_RESOURCE_COMPLIANCE:
            break;
        case AWS_CODE_PIPELINE_PIPELINE:
            break;
        case UNKNOWN_TO_SDK_VERSION:
            break;
        default:
            throw new IllegalStateException("Unexpected value: " + id.resourceType());
        }
    }
}

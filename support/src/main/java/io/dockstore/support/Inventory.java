package io.dockstore.support;

import java.util.ArrayList;
import java.util.List;

import io.dockstore.client.cli.Client;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.model.ListDiscoveredResourcesRequest;
import software.amazon.awssdk.services.config.model.ListDiscoveredResourcesResponse;
import software.amazon.awssdk.services.config.model.ResourceIdentifier;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;

import static io.dockstore.client.cli.ArgumentUtility.outFormatted;

/**
 * You must have installed and configured AWS CLI
 * and your IAM user must be given proper permissions
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
        List<List<String>> output = new ArrayList<>();
        output.add(List.of("Type", "Id", "Name"));
        for (String type : resourceTypes) {
            ListDiscoveredResourcesRequest build1 = ListDiscoveredResourcesRequest.builder().resourceType(type).build();
            ListDiscoveredResourcesResponse listDiscoveredResourcesResponse = build.listDiscoveredResources(build1);
            for (ResourceIdentifier id : listDiscoveredResourcesResponse.resourceIdentifiers()) {

                List<String> strings = new ArrayList<>();
                strings.add(id.resourceTypeAsString());
                strings.add(id.resourceId());
                strings.add(id.resourceName() == null ? "" : id.resourceName());
                processDetailedInfo(id, strings);
                output.add(strings);
            }
        }

        int maxColumns = output.stream().map(List::size).max(Integer::compareTo).get();
        List<Integer> maxWidths = new ArrayList<>();
        for (int i = 0; i < maxColumns; i++) {
            int finalI = i;
            maxWidths.add(output.stream().map(list -> list.size() > finalI ? list.get(finalI).length() : 0).max(Integer::compare).get()
                    + Client.PADDING);
        }
        StringBuilder format = new StringBuilder();
        for (List<String> line : output) {
            for (int i = 0; i < line.size(); i++) {
                format.append("%-").append(maxWidths.get(i)).append("s");
            }
            outFormatted(format.toString(), line.toArray());
            format.delete(0, format.length());
        }
    }

    private static void processDetailedInfo(ResourceIdentifier id, List<String> strings) {
        switch (id.resourceType()) {
        case AWS_EC2_CUSTOMER_GATEWAY:
            break;
        case AWS_EC2_EIP:
            break;
        case AWS_EC2_HOST:
            break;
        case AWS_EC2_INSTANCE:
            Ec2Client build2 = Ec2Client.builder().build();
            DescribeInstancesRequest.Builder builder = DescribeInstancesRequest.builder().instanceIds(id.resourceId());
            DescribeInstancesResponse describeInstancesResponse = build2.describeInstances(builder.build());
            Instance instance = describeInstancesResponse.reservations().get(0).instances().get(0);
            // demo some extra info for EC2 instances
            strings.add(instance.imageId());
            strings.add(instance.instanceTypeAsString());
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

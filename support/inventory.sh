#!/bin/bash
display_usage () {
    echo "usage: $0 [-h] region output"
    echo "  region	(optional) us-east-1 by default"
    echo "  output	(optional) table by default {table,json,text}"
    echo "  -h		display help"
    echo "You must have installed and configured AWS CLI "
    echo "and your IAM user must be given proper permissions."
}

if [[  $1 == "--help" ||  $1 == "-h" || $1 == "help" ]]
then 
  display_usage
  exit 0
fi 

REGION=${1:-us-east-1}
OUTPUT=${2:-table}
echo $REGION
lr () {
   echo $2 $1
   aws configservice list-discovered-resources --resource-type $1 --output $OUTPUT --region $REGION
}

echo "Generating inventory using AWS config service..."

# resource type, description
lr "AWS::ACM::Certificate" "Amazon Certificate Manager"
lr "AWS::ApiGateway::RestApi" "API Gateway"
lr "AWS::CloudFormation::Stack" "CloudFormation Stacks"
lr "AWS::CloudFront::Distribution" "CloudFront Distribution"
lr "AWS::CloudWatch::Alarm" "CloudWatch Alarms"
lr "AWS::EC2::Instance" "EC2 Instances"
lr "AWS::EC2::SecurityGroup" "Security Groups"
lr "AWS::EC2::Subnet" "Subnets"
lr "AWS::EC2::VPC" "Virtual Private Cloud (VPC)"
lr "AWS::ElasticLoadBalancingV2::LoadBalancer" "Load Balancer"
lr "AWS::IAM::Group" "IAM Users (only viewable by admins)"
lr "AWS::IAM::User" "IAM Groups (only viewable by admins)"
lr "AWS::Lambda::Function" "Lambda Function"
lr "AWS::RDS::DBInstance" "Relational Database Service (RDS) Instance"
lr "AWS::S3::Bucket" "S3 Buckets"
lr "AWS::SQS::Queue" "Simple Queue Service (SQS)"
lr "AWS::WAF::WebACL" "Web Application Firewall (WAF)"

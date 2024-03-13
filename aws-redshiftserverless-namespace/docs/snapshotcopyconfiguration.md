# AWS::RedshiftServerless::Namespace SnapshotCopyConfiguration

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#destinationregion" title="DestinationRegion">DestinationRegion</a>" : <i>String</i>,
    "<a href="#destinationkmskeyid" title="DestinationKmsKeyId">DestinationKmsKeyId</a>" : <i>String</i>,
    "<a href="#snapshotretentionperiod" title="SnapshotRetentionPeriod">SnapshotRetentionPeriod</a>" : <i>Integer</i>
}
</pre>

### YAML

<pre>
<a href="#destinationregion" title="DestinationRegion">DestinationRegion</a>: <i>String</i>
<a href="#destinationkmskeyid" title="DestinationKmsKeyId">DestinationKmsKeyId</a>: <i>String</i>
<a href="#snapshotretentionperiod" title="SnapshotRetentionPeriod">SnapshotRetentionPeriod</a>: <i>Integer</i>
</pre>

## Properties

#### DestinationRegion

_Required_: Yes

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### DestinationKmsKeyId

_Required_: No

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### SnapshotRetentionPeriod

_Required_: No

_Type_: Integer

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

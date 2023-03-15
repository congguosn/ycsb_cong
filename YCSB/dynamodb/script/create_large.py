import boto3

dynamodb = boto3.resource('dynamodb',  endpoint_url='http://scylla-dp-4x-az.dataplatform.dev.smartnews.net:8000',
                  region_name='ap-northeast-1', aws_access_key_id='alternator', aws_secret_access_key='secret_pass')


dynamodb.create_table(
    AttributeDefinitions=[
    {
        'AttributeName': 'key',
        'AttributeType': 'S'
    },
    ],
    BillingMode='PAY_PER_REQUEST',
    TableName='usertable_large',
    KeySchema=[
    {
        'AttributeName': 'key',
        'KeyType': 'HASH'
    },
    ])

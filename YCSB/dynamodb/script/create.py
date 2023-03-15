import boto3

dynamodb = boto3.resource('dynamodb',  endpoint_url='http://172.17.0.2:8000',
                  region_name='ap-northeast-1', aws_access_key_id='alternator', aws_secret_access_key='secret_pass')


dynamodb.create_table(
    AttributeDefinitions=[
    {
        'AttributeName': 'key',
        'AttributeType': 'S'
    },
    ],
    BillingMode='PAY_PER_REQUEST',
    TableName='usertable',
    KeySchema=[
    {
        'AttributeName': 'key',
        'KeyType': 'HASH'
    },
    ])

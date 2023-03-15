import boto3

dynamodb = boto3.resource('dynamodb',  endpoint_url='http://scylla-cc-large-2.dataplatform.dev.smartnews.net:8000',
                  region_name='ap-northeast-1', aws_access_key_id='alternator', aws_secret_access_key='secret_pass')

print(dynamodb.batch_get_item(RequestItems={
    'usertable' : { 'Keys': [{ 'key': 'test123' }] }
}))

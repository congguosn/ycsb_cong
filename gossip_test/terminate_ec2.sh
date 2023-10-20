INSTANCE_NAME=$1

INSTANCE_ID=`kubectl describe node $INSTANCE_NAME |grep nodeid|sed '1,$s/  *//g'|cut -d ':' -f3|cut -d ',' -f1`

echo "instance_id="$INSTANCE_ID


CMD="aws ec2 terminate-instances --instance-ids $INSTANCE_ID --profile smartnews-global-prd-exp --region ap-northeast-1"
echo $CMD
#eval $CMD

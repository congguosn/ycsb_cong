POD_NAME=$1

CMD="kubectl logs $POD_NAME -n realtime-streaming scylla | grep -v probe | grep -v compaction > $POD_NAME.log"
echo $CMD
eval $CMD

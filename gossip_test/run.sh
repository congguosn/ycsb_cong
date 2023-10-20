if [ $# -ne 4 ]; then
    echo "$0: <POD_NAME> <KEYSPACE> <TARGET> <RECORD_COUNT>"
    exit -1
fi

POD_NAME=$1
KEYSPACE=$2
TARGET=$3
RECORD_COUNT=$4

kubectl exec -it $POD_NAME -n realtime-streaming -- /bin/bash -c "nohup /opt/YCSB/bin/ycsb run scylla -s -P workloads/workloada -threads 10 -p recordcount=$RECORD_COUNT -p target=$TARGET -p operationcount=$RECORD_COUNT -p readproportion=0.5 -p updateproportion=0.5 -p fieldcount=10 -p fieldlength=10 -p insertstart=0 -p insertcount=$RECORD_COUNT -p scylla.tokenaware=true -p scylla.hosts=\"scylla-gossip-test-a.dataplatform.smartnews.net\" -p scylla.username=cassandra -p scylla.password=cassandra -p scylla.keyspace=$KEYSPACE -p table=\"usertable\"" &

#kubectl exec -it $POD_NAME -n realtime-streaming -- /bin/bash -c "nohup /opt/YCSB/bin/ycsb run scylla -s -P workloads/workloada -threads 10 -p measurement.interval=both -p target=$TARGET -p recordcount=$RECORD_COUNT -p operationcount=$RECORD_COUNT -p readproportion=0.5 -p updateproportion=0.5 -p fieldcount=10 -p fieldlength=10 -p insertstart=0 -p insertcount=$RECORD_COUNT -p scylla.tokenaware=true -p scylla.hosts=\"scylla-gossip-test-a.dataplatform.smartnews.net\" -p scylla.username=cassandra -p scylla.password=cassandra -p scylla.keyspace=$KEYSPACE -p table=\"usertable\"" &

kubectl exec -it $1 -n realtime-streaming -- mvn install -pl scylla -am -Dmaven.test.skip=true

kubectl exec -it $1 -n realtime-streaming -- bin/ycsb run scylla -s -P workloads/workloada -threads 80 -p recordcount=60000000 -p readpropotion=0.25 -p updateproportion=0.75 -p fieldcount=1 -p fieldlength=6000 -p insertstart=0 -p insertcount=60000000 -p scylla.username=cassandra -p scylla.password=cassandra -p scylla.hosts="scylla-dp-4x-az.dataplatform.dev.smartnews.net" -p table="usertable_uuid" -p scylla.port=19042 -p requestdistribution=zipfian

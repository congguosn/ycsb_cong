ycsb load dynamodb -p measurement.interval=both -p target=20000 -P dynamodb/workload/workloada -P dynamodb/conf/dynamodb.properties -threads 100
ycsb run dynamodb -p measurement.interval=both -p target=20000 -P dynamodb/workload/workloada -P dynamodb/conf/dynamodb.properties -threads 100

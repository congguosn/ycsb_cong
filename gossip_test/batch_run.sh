for ((i=1; i<=5; i++))
do
    DEPLOYMENT="ycsb-client-$i"
    POD_NAMES=`kubectl get pod -n realtime-streaming | grep $DEPLOYMENT | cut -d ' ' -f1`
    #echo $POD_NAMES
    for POD_NAME in $POD_NAMES
    do
        COMMAND="nohup ./run.sh $POD_NAME ycsb_$i 1000 1000000 > $POD_NAME.run.log"
        echo $COMMAND
        eval $COMMAND
    done
done

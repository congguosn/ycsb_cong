for ((i=1; i<=5; i++))
do
    DEPLOYMENT="ycsb-client-$i"
    POD_NAME=`kubectl get pod -n realtime-streaming | grep $DEPLOYMENT | cut -d ' ' -f1|head -1`
    #echo $POD_NAME
    COMMAND="nohup ./load.sh $POD_NAME ycsb_$i 10000 10000000 > $POD_NAME.load.log"
    echo $COMMAND
    eval $COMMAND
done

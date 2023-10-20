POD_NAME=$1
CMD=$2

kubectl exec -it $POD_NAME -n realtime-streaming -- $CMD

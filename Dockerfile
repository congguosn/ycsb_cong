FROM maven:3.8.6-jdk-11

RUN mkdir -p /opt
ADD YCSB.tar.gz /opt
COPY start.sh /opt/start.sh
COPY commands /opt/commands
RUN chmod +x /opt/start.sh

RUN apt-get update && apt-get install -y python2.7
RUN ln -s /usr/bin/python2.7 /usr/bin/python

WORKDIR "/opt/YCSB"

ENTRYPOINT ["/opt/start.sh"]

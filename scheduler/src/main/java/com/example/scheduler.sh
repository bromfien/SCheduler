#bash
cd /home/bromfien/Documents/Java/Scheduler/scheduler
mvn compile -q
mvn exec:java -Daggregator=http://bromfien.duckdns.org:8080/push

#to connect to gcloud
gcloud compute ssh scheduler-vm --zone=us-east1-d
tail -f ~/scheduler.log
## Shove


Have you ever wanted to inject messages into Kafka for basic testing.  No?  Well, I did. 

Shove allows you to specify a broker, a topic, a key, and content that you'd like to shove into Kafka (get it?!) so that you can more-easily test your downstream services without writing a script.  

Look, I couldn't find any other GUI to do this, so I thought I'd build it. 


## Installation

This is a pretty typical 

`lein deps` and `lein uberjar`


If you aren't interested in Clojure's build system, you can download the self-contained JAR file from the "Releases" page. I've been trying to keep that page relatively updated. 


## Roadmap

- ~~ An option to import a CSV file to do a lot of batch-inserts. ~~
- Utilize Zookeeper to start mimicking the features of Kafka tool. 
- A mechanism for basic ad-hoc transformations from one topic to another. 

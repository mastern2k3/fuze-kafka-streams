
# fuze-kafka-streams

This kit is composed of several components:

- **`tweet-tap`** - Listens to tweets and injects them as messages to Kafka.
- **`stream-client`** - An example Kafka streams client that listens to the tweet stream and builds a `KTable` of word-count from the tweet text content.
- **`resources`** - Contains artifacts related to the kit.
  - `docker-compose.yml` - A *docker-compose* file that can be used to set up a full environment.  
    After setting up the environment several endpoints will be available:
    - http://localhost:8000 - A web application for browsing topics and messages.

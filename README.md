![JMSscala Logo](http://felstar.com/projects/jmsScala/img/jms-scala-cliff.png)
# JMS for Scala (JMSscala)

*Author: [Dino Fancellu](http://dinofancellu.com)*

To use as a library, just pull in from

[https://jitpack.io/#fancellu/jmsScala](https://jitpack.io/#fancellu/jmsScala)

[![Build Status](https://travis-ci.org/fancellu/jmsScala.svg?branch=master)](https://travis-ci.org/fancellu/jmsScala)

**JMSscala** is a Scala Library to invoke JMS, the Java Message System.

It provides Scala interfaces, metaphors and conversions that lead to tighter code and less boilerplate

It should work with any compliant **JMS** driver, having already been tested against **[ActiveMQ 5.8.0](http://activemq.apache.org/download.html)** drivers

Requires Scala 2.10.3/2.11.7 and JMS 1.1 

Firstly, make sure that your **JMS** java driver jars are included and are working.
Perhaps run some java to make sure its all up and running.

Then in your Scala include in the following:

	import com.felstar.jmsScala.JMS._
    import com.felstar.jmsScala.JMS.AllImplicits._

The next few steps are very familiar to any JMS Developer:

Create your session, e.g.
```scala
val connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616")
val connection = connectionFactory.createConnection()
connection.start
      
val session = connection.session(false, Session.AUTO_ACKNOWLEDGE)
```
The above uri and connection factory is dependent on the driver used
	
# Some example code: #

## Connect to a queue and purge existing messages ##
```scala
val q=session.queue("yikes")      
val messageConsumer = q.consumer
  	// clean up queue, consuming all messages
messageConsumer.purge
```
  
## Send a couple of text messages ##
```scala
val prod = q.producer.deliveryMode(NON_PERSISTENT).send("Hello").send("World")
```
## Send a map message with a couple of properties ##
```scala
prod.sendMapWith(Map("one" -> 1, "two" -> 2))
 {_.propertiesMap(Map("someprop" -> "hello", "anotherprop" -> "Goodbye"))}
```
## Show the 3 current messages on the queue ##
```scala
val messages=q.browser().messages
  println(messages.map{
    case mess:TextMessage=>mess.asText
    case mess:MapMessage=>mess.asMap
  }.map("BROWSE "+_).mkString("\n"))
```
>     BROWSE Hello
>     BROWSE World
>     BROWSE Map(two -> 2, one -> 1)

## Consume and print the first 2 messages ##
```scala
println(messageConsumer.receiveText)
println(messageConsumer.receiveText)
```
>     Hello
>     World

## Consume 3rd map message and its properties ##
```scala
   // we don't get map directly, but get message
   // so that we can get both map and propertiesMap
val message = messageConsumer.receive()

println(message.asMap)
println(message.propertiesMap)
```
>     Map(two -> 2, one -> 1)
>     Map(anotherprop -> Goodbye, someprop -> hello)

## Working with a temporary queue ##
```scala
val tq=session.temporaryQueue      
tq.producer.send("A temp message").closeMe()
    // get the text back
val tqConsumer=tq.consumer
println("TemporaryQueue: "+tqConsumer.receiveText)
tqConsumer.closeMe()
```
>     TemporaryQueue: A temp message

## Async messaging ##
```scala
echoActor ! "About to set up async message consumer"
Thread.sleep(1000)
   // receives async incoming messages, routes to actor
messageConsumer.setMessageListener(new MessageListener(){
   def onMessage(mess:javax.jms.Message)=  echoActor ! mess         
})
   // the following should be seen in the echoActor
prod.send("Hello, this should be seen async")
```
## echoActor ##
```scala
val echoActor = actor {
    while (true) {
      receive {
        case Stop=>exit()
        case mess:Message =>{
               println("ASYNC "+Thread.currentThread().getName())
               println("ASYNC "+mess.asText)
        }
        case unknown =>println("received unknown message: "+ unknown)
      }
    }
  }
```
>     received unknown message: About to set up async message consumer
>     ASYNC ForkJoinPool-1-worker-29
>     ASYNC Hello, this should be seen async

## Working with anonymous producer ##
```scala
val anonProd=session.anonProducer   
  // when sending we specify destination   
anonProd.send("Sent from anon producer",q).closeMe() 
```
>     ASYNC ForkJoinPool-1-worker-29
>     ASYNC Sent from anon producer

## Filtering consumer ##
```scala
messageConsumer.closeMe()
echoActor ! Stop
  // setup consumer to just handle certain messages
val filteringConsumer=q.consumer("type='misc'")
        
prod.send("Ignored: Hopefully not seen by filtering consumer")
prod.sendWith("Is this seen"){_.propertiesMap(Map("type"->"misc"))}
prod.sendWith("This too?"){_.propertiesMap(Map("type"->"misc","amount"->42))}
  
var text:String=null
do
{
 text=filteringConsumer.receiveText(1000)       
 if (text!=null) println("Filtering consumer: "+text)       
} while (text!=null)

  // will leave Ignored on the queue
   
filteringConsumer.closeMe()  
```
>     Filtering consumer: Is this seen
>     Filtering consumer: This too?

## Working with topics ##
```scala
prod.closeMe()       
        
  // create 3 topic consumers, but last one is created after message sent
val topic=session.topic("messageboard")
      
val tc1=topic.consumer
val tc2=topic.consumer
val tp=topic.producer.send("Here's a shout out!").closeMe()
val tc3=topic.consumer
  // first 2 sees the shout out text
println("From topic via tc1 "+tc1.receiveText)
tc1.closeMe()
println("From topic via tc2 "+tc2.receiveText)
tc2.closeMe()
  // 3rd will not see it, returns null after 1 second timeout
println("From topic via tc3 "+tc3.receiveText(1000))
tc3.closeMe()
 
session.closeMyConnection()
```
>      From topic via tc1 Here's a shout out!
>      From topic via tc2 Here's a shout out!
>      From topic via tc3 null

##A few items of note

Use of closeMe() is to close up resource allocation as we go along. 
You may not wish to do so and could just rely on a final session.closeMe() or session.closeMyConncection()

Any feedback is appreciated. I understand that I may well not currently cover all use cases and look forward to improving jmsScala.
You can find some examples of jmsScala being used in the com.felstar.jmsScala.example package

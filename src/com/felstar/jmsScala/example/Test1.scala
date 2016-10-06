package com.felstar.jmsScala.example

import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.DeliveryMode._
import javax.jms.Session
import com.felstar.jmsScala.JMS._
import com.felstar.jmsScala.JMS.AllImplicits._
import javax.jms.MessageListener
import javax.jms.Message
import javax.jms.TextMessage
import javax.jms.MapMessage

object Test1{

import scala.actors.Actor
import scala.actors.Actor._

 case object Stop

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
  
  def main(args: Array[String]): Unit = {
    val connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616")

      val connection = connectionFactory.createConnection()
      connection.start()
      
      val session = connection.session(false, Session.AUTO_ACKNOWLEDGE)

      val q=session.queue("yikes")
      
      val messageConsumer = q.consumer
      	// clean up queue, consuming all messages
      messageConsumer.purge()
        // send a couple of text messages
      val prod = q.producer.deliveryMode(NON_PERSISTENT)
        .send("Hello").send("World")
       // send a map message with a couple of properties
      prod.sendMapWith(Map("one" -> 1, "two" -> 2))
       {_.propertiesMap(Map("someprop" -> "hello", "anotherprop" -> "Goodbye"))}
       // show the 3 current messages on the queue
      val messages=q.browser().messages
      println(messages.map{
        case mess:TextMessage=>mess.asText
        case mess:MapMessage=>mess.asMap
      }.map("BROWSE "+_).mkString("\n"))
       // consume and print a the first 2 messages
      println(messageConsumer.receiveText)
      println(messageConsumer.receiveText)

      // we don't get map directly, but get message
      // so that we can get both map and propertiesMap
      val message = messageConsumer.receive()

      println(message.asMap)
      println(message.propertiesMap)

      	// create own internal queue, send text message
      val tq=session.temporaryQueue()
      tq.producer.send("A temp message").closeMe()
       // get the text back
      val tqConsumer=tq.consumer
      println("TemporaryQueue: "+tqConsumer.receiveText)
      tqConsumer.closeMe()
      
       // setup listener to route incoming to echoActor
      echoActor ! "About to set up async message consumer"
      Thread.sleep(1000)
       // receives async incoming messages, routes to actor
       messageConsumer.setMessageListener(new MessageListener(){
          def onMessage(mess:javax.jms.Message)=  echoActor ! mess         
       })
       // the following should be seen in the echoActor
      prod.send("Hello, this should be seen async")
      
       // create an anonymous producer
      
      val anonProd=session.anonProducer()
       // when sending we specify destination
      anonProd.send("Sent from anon producer",q).closeMe()      
      
      Thread.sleep(1000)
       // shut down the async message handling
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
      
        // will leave ignored on the queue
       
       filteringConsumer.closeMe()
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
    }
}
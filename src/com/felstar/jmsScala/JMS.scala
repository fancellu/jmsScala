package com.felstar.jmsScala

import javax.jms._

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * JMSscala is a Scala API that sits atop JMS and provides Scala interfaces and metaphors
  * Comments and suggestions are welcome. Use this file as you will.
  * Would be nice if I got attribution. Thanks.
  *
  * @author Dino Fancellu (Felstar Ltd)
  * @version 0.80
  *
  */
object JMS {

  import AllImplicits._

  private val session2connection = mutable.WeakHashMap[Session, Connection]()
  private val dest2session = mutable.WeakHashMap[Destination, Session]()
  private val prod2session = mutable.WeakHashMap[MessageProducer, Session]()
  private val cons2session = mutable.WeakHashMap[MessageConsumer, Session]()

  trait ImplicitConnection {

    implicit class MyConnection(conn: Connection) {
      def session(transacted: Boolean, acknowledgeMode: Int): Session = {
        val sess = conn.createSession(transacted, acknowledgeMode)
        session2connection += sess -> conn
        sess
      }
    }

  }

  trait ImplicitSession {

    implicit class MySession(sess: Session) {
      def queue(queueName: String): Queue = {
        val queue = sess.createQueue(queueName)
        dest2session += queue -> sess
        queue
      }

      def temporaryQueue(): TemporaryQueue = {
        val queue: TemporaryQueue = sess.createTemporaryQueue
        dest2session += queue -> sess
        queue
      }

      def topic(topicName: String): Topic = {
        val topic = sess.createTopic(topicName)
        dest2session += topic -> sess
        topic
      }

      def temporaryTopic(): TemporaryTopic = {
        val topic = sess.createTemporaryTopic
        dest2session += topic -> sess
        topic
      }

      def anonProducer(): MessageProducer = {
        val prod = sess.createProducer(null)
        prod2session += prod -> sess
        prod
      }

      def closeMe(): Unit = {
        if (sess != null) sess.close()
        synchronized {
          session2connection -= sess
          dest2session.retain { case (_, session) => session != sess }
          prod2session.retain { case (_, session) => session != sess }
          cons2session.retain { case (_, session) => session != sess }
        }
      }

      def closeMyConnection(): Unit = {
        synchronized {
          session2connection.get(sess).foreach(_.close)
          closeMe()
        }
      }
    }

  }

  trait ImplicitDestination {

    implicit class MyDestination(dest: Destination) {
      def producer: MessageProducer = {
        val session = dest2session(dest)
        val prod = session.createProducer(dest)
        prod2session += prod -> session
        prod
      }

      def consumer: MessageConsumer = {
        val session = dest2session(dest)
        val con = session.createConsumer(dest)
        cons2session += con -> session
        con
      }

      def consumer(messageSelector: String, noLocal: Boolean = false): MessageConsumer = {
        val session = dest2session(dest)
        val con = session.createConsumer(dest, messageSelector, noLocal)
        cons2session += con -> session
        con
      }
    }

  }

  trait ImplicitTopic {

    implicit class MyTopic(topic: Topic) {
      def durable(name: String): TopicSubscriber = {
        val session = dest2session(topic)
        val sub = session.createDurableSubscriber(topic, name)
        //cons2session+=con->session
        sub
      }

      def durable(name: String, messageSelector: String, noLocal: Boolean = false): TopicSubscriber = {
        val session = dest2session(topic)
        val sub = session.createDurableSubscriber(topic, name, messageSelector, noLocal)
        //cons2session+=con->session
        sub
      }
    }

  }

  trait ImplicitQueue {

    implicit class MyQueue(queue: Queue) {
      def browser(messageSelector: String = null): QueueBrowser =
        dest2session(queue).createBrowser(queue, messageSelector)
    }

  }

  type MapMessageType = Map[String, Any]

  trait ImplicitProducer {

    implicit class MyProducer(prod: MessageProducer) {

      def create(text: String): TextMessage =
        prod2session(prod).createTextMessage(text)

      def sendWith(text: String, dest: Destination = null)(f: Message => Unit): MessageProducer = {
        val mess = create(text)
        f(mess)
        if (dest == null) prod.send(mess) else prod.send(dest, mess)
        prod
      }

      def send(text: String, dest: Destination = null): MessageProducer = {
        val mess = create(text)
        if (dest == null) prod.send(mess) else prod.send(dest, mess)
        prod
      }

      def create(map: MapMessageType): MapMessage = {
        val mapMessage = prod2session(prod).createMapMessage()
        map.foreach { case (k, v) => mapMessage.setObject(k, v) }
        mapMessage
      }

      def sendMapWith(map: MapMessageType, dest: Destination = null)(f: Message => Unit): MessageProducer = {
        val mess = create(map)
        f(mess)
        if (dest == null) prod.send(mess) else prod.send(dest, mess)
        prod
      }

      def sendMap(map: MapMessageType, dest: Destination = null): MessageProducer = {
        val mess = create(map)
        if (dest == null) prod.send(mess) else prod.send(dest, mess)
        prod
      }

      def deliveryMode(mode: Int): MessageProducer = {
        prod.setDeliveryMode(mode)
        prod
      }

      def disableMessageID(value: Boolean): MessageProducer = {
        prod.setDisableMessageID(value)
        prod
      }

      def disableMessageTimestamp(value: Boolean): MessageProducer = {
        prod.setDisableMessageTimestamp(value)
        prod
      }

      def priority(defaultPriority: Int): MessageProducer = {
        prod.setPriority(defaultPriority)
        prod
      }

      def timeToLive(timeToLive: Int): MessageProducer = {
        prod.setTimeToLive(timeToLive)
        prod
      }

      def closeMe(): Unit = {
        prod.close()
        prod2session -= prod
      }
    }

  }

  trait ImplicitConsumer {

    implicit class MyConsumer(con: MessageConsumer) {
      def receiveText: String = receiveText(0)

      def receiveText(timeout: Long = 0): String = con.receive(timeout).asText

      def receiveMap: MapMessageType = receiveMap(0)

      def receiveMap(timeout: Long = 0): MapMessageType = con.receive(timeout).asMap

      def listen(callback: Message => Unit): Unit = {
        con.setMessageListener(
          new MessageListener {
            def onMessage(x: Message) = callback(x)
          }
        )
      }

      def purge(): Unit = {
        while (con.receive(100) != null) {}
      }

      def closeMe(): Unit = {
        con.close()
        cons2session -= con
      }
    }

  }

  trait ImplicitMessage {

    implicit class MyMessage(mess: Message) {
      def propertiesMap: MapMessageType =
        mess.getPropertyNames.asScala
          .map { name =>
            val nameString = name.toString
            nameString -> mess.getObjectProperty(nameString)
          }.toMap


      def propertiesMap(map: MapMessageType): Message = {
        map.foreach { case (k, v) => mess.setObjectProperty(k, v) }
        mess
      }

      def asMap: MapMessageType = {
        if (mess == null) return null
        val mm = mess.asInstanceOf[javax.jms.MapMessage]
        mm.getMapNames.asScala
          .map { name =>
            val nameString = name.toString
            nameString -> mm.getObject(nameString)
          }.toMap
      }

      def asText: String = {
        if (mess == null) null else mess.asInstanceOf[javax.jms.TextMessage].getText
      }

      def asBytes: Array[Byte] = {
        if (mess == null) null
        else {
          val byteMess = mess.asInstanceOf[javax.jms.BytesMessage]
          val length = byteMess.getBodyLength.toInt
          val dest = new Array[Byte](length)
          val bytesRead = byteMess.readBytes(dest, length)
          if (bytesRead != byteMess.getBodyLength)
            throw new ArrayIndexOutOfBoundsException("Attempt to read message from JMS BytesMessage different number of bytes than indicated by body length")
          dest
        }
      }
    }

  }

  trait ImplicitQueueBrowser {

    implicit class MyQueueBrowser(browser: QueueBrowser) {
      def messages(): Seq[Message] = {
        val result = browser.getEnumeration.asScala.collect { case m: Message => m }.toSeq.reverse
        browser.close()
        result
      }
    }
  }

  object AllImplicits
    extends ImplicitConnection with ImplicitSession with ImplicitDestination
      with ImplicitProducer with ImplicitConsumer with ImplicitMessage
      with ImplicitQueueBrowser with ImplicitTopic with ImplicitQueue

}

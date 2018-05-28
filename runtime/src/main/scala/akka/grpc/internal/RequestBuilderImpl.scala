/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.internal

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.annotation.InternalApi
import akka.dispatch.ExecutionContexts
import akka.grpc.{ GrpcResponseMetadata, GrpcSingleResponse }
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JavaSource }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.util.ByteString
import io.grpc._

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

// request builder implementations for the generated clients
// note that these are created in generated code in arbitrary user packages
// so must remain public even though internal

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaUnaryRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.scaladsl.SingleResponseRequestBuilder[I, O]
  with MetadataOperations[ScalaUnaryRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(descriptor: MethodDescriptor[I, O], channel: Channel, options: CallOptions) =
    this(descriptor, channel, options, MetadataImpl.empty)

  override def invoke(request: I): Future[O] = {
    val listener = new UnaryCallAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, headers.toGoogleGrpcMetadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.future
  }

  override def invokeWithMetadata(request: I): Future[GrpcSingleResponse[O]] = {
    val listener = new UnaryCallWithMetadataAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, headers.toGoogleGrpcMetadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.future
  }

  override def withHeaders(headers: MetadataImpl): ScalaUnaryRequestBuilder[I, O] =
    new ScalaUnaryRequestBuilder[I, O](descriptor, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaUnaryRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.javadsl.SingleResponseRequestBuilder[I, O]
  with MetadataOperations[JavaUnaryRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(descriptor: MethodDescriptor[I, O], channel: Channel, options: CallOptions) =
    this(descriptor, channel, options, MetadataImpl.empty)

  override def invoke(request: I): CompletionStage[O] = {
    val listener = new UnaryCallAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, headers.toGoogleGrpcMetadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.cs
  }

  override def invokeWithMetadata(request: I): CompletionStage[GrpcSingleResponse[O]] = {
    val listener = new UnaryCallWithMetadataAdapter[O]
    val call = channel.newCall(descriptor, options)
    call.start(listener, headers.toGoogleGrpcMetadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(2)
    listener.cs
  }

  override def withHeaders(headers: MetadataImpl): JavaUnaryRequestBuilder[I, O] =
    new JavaUnaryRequestBuilder[I, O](descriptor, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaClientStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl,
  materializer: Materializer)
  extends akka.grpc.scaladsl.SingleResponseRequestBuilder[Source[I, NotUsed], O]
  with MetadataOperations[ScalaClientStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions,
    materializer: Materializer) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty, materializer)

  override def invoke(request: Source[I, NotUsed]): Future[O] =
    invokeWithMetadata(request).map(_.value)(ExecutionContexts.sameThreadExecutionContext)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Future[GrpcSingleResponse[O]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, false, headers))
    val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
      source.viaMat(flow)(Keep.right)
        .toMat(Sink.head)(Keep.both)
        .run()(materializer)

    metadataFuture.zip(resultFuture).map {
      case (metadata, result) =>
        new GrpcSingleResponse[O] {
          def value: O = result
          def getValue(): O = result
          def headers = metadata.headers
          def getHeaders() = metadata.getHeaders()
          def trailers = metadata.trailers
          def getTrailers = metadata.getTrailers()
        }
    }(ExecutionContexts.sameThreadExecutionContext)
  }

  override def withHeaders(headers: MetadataImpl): ScalaClientStreamingRequestBuilder[I, O] =
    new ScalaClientStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers, materializer)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaClientStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl,
  materializer: Materializer)
  extends akka.grpc.javadsl.SingleResponseRequestBuilder[JavaSource[I, NotUsed], O]
  with MetadataOperations[JavaClientStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions,
    materializer: Materializer) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty, materializer)

  override def invoke(request: JavaSource[I, NotUsed]): CompletionStage[O] =
    invokeWithMetadata(request).thenApply(_.value)

  override def invokeWithMetadata(source: JavaSource[I, NotUsed]): CompletionStage[GrpcSingleResponse[O]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, false, headers))
    val (metadataFuture: Future[GrpcResponseMetadata], resultFuture: Future[O]) =
      source.asScala
        .viaMat(flow)(Keep.right)
        .toMat(Sink.head)(Keep.both)
        .run()(materializer)

    metadataFuture.zip(resultFuture).map {
      case (metadata, result) =>
        new GrpcSingleResponse[O] {
          def value: O = result
          def getValue(): O = result
          def headers = metadata.headers
          def getHeaders() = metadata.getHeaders()
          def trailers = metadata.trailers
          def getTrailers = metadata.getTrailers()
        }
    }(ExecutionContexts.sameThreadExecutionContext).toJava
  }

  override def withHeaders(headers: MetadataImpl): JavaClientStreamingRequestBuilder[I, O] =
    new JavaClientStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers, materializer)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaServerStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.scaladsl.StreamResponseRequestBuilder[I, O]
  with MetadataOperations[ScalaServerStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty)

  override def invoke(request: I): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: I): Source[O, Future[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true, headers))
    Source.single(source).viaMat(flow)(Keep.right)
  }

  override def withHeaders(headers: MetadataImpl): ScalaServerStreamingRequestBuilder[I, O] =
    new ScalaServerStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaServerStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.javadsl.StreamResponseRequestBuilder[I, O]
  with MetadataOperations[JavaServerStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty)

  override def invoke(request: I): JavaSource[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: I): JavaSource[O, CompletionStage[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true, headers))
    Source.single(source).viaMat(flow)(Keep.right).mapMaterializedValue(_.toJava).asJava
  }

  override def withHeaders(headers: MetadataImpl): JavaServerStreamingRequestBuilder[I, O] =
    new JavaServerStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class ScalaBidirectionalStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.scaladsl.StreamResponseRequestBuilder[Source[I, NotUsed], O]
  with MetadataOperations[ScalaBidirectionalStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty)

  override def invoke(request: Source[I, NotUsed]): Source[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: Source[I, NotUsed]): Source[O, Future[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true, headers))
    source.viaMat(flow)(Keep.right)
  }

  override def withHeaders(headers: MetadataImpl): ScalaBidirectionalStreamingRequestBuilder[I, O] =
    new ScalaBidirectionalStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class JavaBidirectionalStreamingRequestBuilder[I, O](
  descriptor: MethodDescriptor[I, O],
  fqMethodName: String,
  channel: Channel,
  options: CallOptions,
  val headers: MetadataImpl)
  extends akka.grpc.javadsl.StreamResponseRequestBuilder[JavaSource[I, NotUsed], O]
  with MetadataOperations[JavaBidirectionalStreamingRequestBuilder[I, O]] {

  // aux constructor for defaults
  def this(
    descriptor: MethodDescriptor[I, O],
    fqMethodName: String,
    channel: Channel,
    options: CallOptions) =
    this(descriptor, fqMethodName, channel, options, MetadataImpl.empty)

  override def invoke(request: JavaSource[I, NotUsed]): JavaSource[O, NotUsed] =
    invokeWithMetadata(request).mapMaterializedValue(_ => NotUsed)

  override def invokeWithMetadata(source: JavaSource[I, NotUsed]): JavaSource[O, CompletionStage[GrpcResponseMetadata]] = {
    // a bit much overhead here because we are using the flow to represent a single response
    val flow = Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, fqMethodName, channel, options, true, headers))
    source.asScala.viaMat(flow)(Keep.right).mapMaterializedValue(_.toJava).asJava
  }

  override def withHeaders(headers: MetadataImpl): JavaBidirectionalStreamingRequestBuilder[I, O] =
    new JavaBidirectionalStreamingRequestBuilder[I, O](descriptor, fqMethodName, channel, options, headers)

}

/**
 * INTERNAL API
 */
@InternalApi
trait MetadataOperations[T <: MetadataOperations[T]] {
  def headers: MetadataImpl
  def withHeaders(headers: MetadataImpl): T

  def withHeader(key: String, value: String): T =
    withHeaders(headers = headers.withEntry(key, value))

  def withHeader(key: String, value: ByteString): T =
    withHeaders(headers = headers.withEntry(key, value))

}
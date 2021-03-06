package com.thoughtworks.deeplearning

import com.thoughtworks.deeplearning.Layer.{Batch, CloseableOnce}
import com.thoughtworks.deeplearning.Lift._
import com.thoughtworks.deeplearning.DifferentiableSeq.Layers.{Get, ToSeq}
import com.thoughtworks.deeplearning.Lift.Layers.Literal
import shapeless.Lazy

import language.implicitConversions
import language.higherKinds

// TODO: rename to sized

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
object DifferentiableSeq {

  object Layers {

    final case class Get[Input0 <: Batch, ElementData, ElementDelta](
        operand0: Layer.Aux[Input0, Batch.Aux[Seq[ElementData], (Int, ElementDelta)]],
        i: Int
    ) extends Layer {

      final class Output private[Get] (upstream: Batch.Aux[Seq[ElementData], (Int, ElementDelta)]) extends Batch {

        override val isTrainable = upstream.isTrainable

        type Delta = ElementDelta
        type Data = ElementData
        override protected def forceBackward(delta: ElementDelta): Unit = {
          upstream.backward((i, delta))
        }

        override def addReference() = new Output(upstream.addReference())

        override def close(): Unit = {
          upstream.close()
        }

        override val value = {
          upstream.value(i)
        }

      }
      override type Input = Input0

      // TODO: Support tail Int
      override def forward(input: Input) = new Output(operand0.forward(input))

    }

    final case class ToSeq[Input0 <: Batch, ElementData, ElementDelta](
        operands: Seq[Layer.Aux[Input0, Batch.Aux[ElementData, ElementDelta]]])
        extends Layer {

      type Input = Input0

      final class Output private[ToSeq] (upstreams: Seq[Batch.Aux[ElementData, ElementDelta]])
          extends Batch
          with CloseableOnce {

        override type Data = Seq[ElementData]
        override type Delta = (Int, ElementDelta)

        override val isTrainable = upstreams.exists(_.isTrainable)

        override protected def forceBackward(pair: (Int, ElementDelta)): Unit = {
          val (i, delta) = pair
          upstreams(i).backward(delta)
        }

        override val value = {
          upstreams.map(_.value)
        }

        override def close(): Unit = {
          super.close()
          upstreams.foreach(_.close())
        }

        override def addReference() = new Output(upstreams.map(_.addReference()))

      }

      override def forward(input: Input) = new Output(operands.map(_.forward(input)))

    }

  }

  private[deeplearning] type SeqPlaceholder[A <: Placeholder[_, _]] =
    Placeholder[Seq[Placeholder.DataOf[A]], (Int, Placeholder.DeltaOf[A])]

  final class SeqLayerOps[Input <: Batch, ElementData, ElementDelta](
      seqLayer: Layer.Aux[Input, Batch.Aux[Seq[ElementData], (Int, ElementDelta)]]) {

    def apply(i: Int): Layer.Aux[Input, Batch.Aux[ElementData, ElementDelta]] = {
      Get[Input, ElementData, ElementDelta](seqLayer, i)
    }

  }

  implicit def toSeqLayerOps[From, Input <: Batch, SeqData, SeqDelta, ElementData, ElementDelta](from: From)(
      implicit toLayer: ToLayer.Aux[From, Input, SeqData, SeqDelta],
      toSeqLayer: Layer.Aux[Input, Batch.Aux[SeqData, SeqDelta]] <:< Layer.Aux[
        Input,
        Batch.Aux[Seq[ElementData], (Int, ElementDelta)]]
  ): SeqLayerOps[Input, ElementData, ElementDelta] = {
    new SeqLayerOps[Input, ElementData, ElementDelta](toSeqLayer(toLayer(from)))
  }

  implicit def seqToLayer[From, Input0 <: Batch, ElementData, ElementDelta](
      implicit elementToLayer: Lazy[ToLayer.Aux[From, Input0, ElementData, ElementDelta]])
    : ToLayer.Aux[Seq[From], Input0, Seq[ElementData], (Int, ElementDelta)] = {
    new ToLayer[Seq[From], Input0] {
      type OutputData = Seq[ElementData]
      type OutputDelta = (Int, ElementDelta)

      override def apply(layers: Seq[From]): Layer.Aux[Input0, Batch.Aux[Seq[ElementData], (Int, ElementDelta)]] = {
        ToSeq[Input0, ElementData, ElementDelta](layers.map(elementToLayer.value(_)))
      }
    }
  }

}

package com.thoughtworks.deeplearning

import cats.Eval
import com.thoughtworks.deeplearning.Lift._
import com.thoughtworks.deeplearning.DifferentiableINDArray._
import com.thoughtworks.deeplearning.DifferentiableINDArray.Optimizers.LearningRate
import com.thoughtworks.deeplearning.DifferentiableDouble._
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4s.Implicits._
import org.scalatest._

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class LayerSpec extends FreeSpec with Matchers {

  implicit val learningRate = new LearningRate {
    override def currentLearningRate() = 0.0003
  }

  "INDArrayPlaceholder dot INDArrayPlaceholder" in {

    def makeNetwork(implicit x: shapeless.the.`From[INDArray]`.Out) = {
      val weightInitialValue =
        Array(
          Array(0.0, 5.0)
        )
      -weightInitialValue.toNDArray.toWeight.dot(x)
    }

    val network = makeNetwork

    val inputData =
      Array(
        Array(2.5, -3.2, -19.5),
        Array(7.5, -5.4, 4.5)
      )

    def train() = {
      val outputBatch = network.forward(inputData.toNDArray.toBatch)
      try {
        val loss = (outputBatch.value: INDArray).sumT
        outputBatch.backward(outputBatch.value)
        loss
      } finally {
        outputBatch.close()
      }
    }

    train().value should be(-33.0)

    for (_ <- 0 until 100) {
      train().value
    }

    math.abs(train().value) should be < 1.0

  }

}

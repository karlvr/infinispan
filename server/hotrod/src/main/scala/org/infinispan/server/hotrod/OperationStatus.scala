package org.infinispan.server.hotrod

/**
 * Hot Rod operation possible status outcomes.
 *
 * @author Galder Zamarreño
 * @since 4.1
 */
object OperationStatus extends Enumeration {
   type OperationStatus = Value

   val Success = Value(0x00)
   val OperationNotExecuted = Value(0x01)
   val KeyDoesNotExist = Value(0x02)
   val SuccessWithPrevious = Value(0x03)
   val NotExecutedWithPrevious = Value(0x04)
   val InvalidIteration = Value(0x05)

   val SuccessCompat = Value(0x06)
   val SuccessWithPreviousCompat = Value(0x07)
   val NotExecutedWithPreviousCompat = Value(0x08)

   val InvalidMagicOrMsgId = Value(0x81)
   val UnknownOperation = Value(0x82)
   val UnknownVersion = Value(0x83) // todo: test
   val ParseError = Value(0x84) // todo: test
   val ServerError = Value(0x85) // todo: test
   val OperationTimedOut = Value(0x86) // todo: test
   val NodeSuspected = Value(0x87)
   val IllegalLifecycleState = Value(0x88)

   def withCompatibility(st: OperationStatus, isCompatibilityEnabled: Boolean): OperationStatus = {
      st match {
         case Success if isCompatibilityEnabled => SuccessCompat;
         case SuccessWithPrevious if isCompatibilityEnabled => SuccessWithPreviousCompat;
         case NotExecutedWithPrevious if isCompatibilityEnabled => NotExecutedWithPreviousCompat;
         case _ => st
      }
   }
}

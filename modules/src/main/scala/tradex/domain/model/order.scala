package tradex.domain
package model

import java.time.LocalDateTime
import java.time.Instant
import java.util.UUID

import cats.data.NonEmptyList
import cats.implicits._
import cats.instances.list._

import instrument._
import account._
import common._
import NewtypeRefinedOps._
import newtypes._
import enums._

object order {
  private[domain] final case class LineItem(
      instrument: ISINCode,
      quantity: Quantity,
      unitPrice: UnitPrice,
      buySell: BuySell
  )

  private[domain] final case class Order(
      no: OrderNo,
      date: LocalDateTime,
      accountNo: AccountNo,
      items: NonEmptyList[LineItem]
  )

  private[domain] final case class FrontOfficeOrder(
      accountNo: String,
      date: Instant,
      isin: String,
      qty: BigDecimal,
      unitPrice: BigDecimal,
      buySell: String
  )

  object Order {
    /**
      * Domain validation for `FrontOfficeOrder` is done here. Creates
      * records after validation
      */
    private[domain] def create(
        frontOfficeOrders: NonEmptyList[FrontOfficeOrder]
    ): ErrorOr[List[Order]] = {
      frontOfficeOrders.toList
        .groupBy(_.accountNo)
        .map {
          case (ano, forders) =>
            makeOrder(UUID.randomUUID.toString, today, ano, forders)
        }
        .toList
        .sequence
        .toEither
    }

    private[domain] def makeOrder(
        ono: String,
        odt: LocalDateTime,
        ano: String,
        forders: List[FrontOfficeOrder]
    ): ValidationResult[Order] = {
      forders
        .map { fo =>
          makeLineItem(fo.isin, fo.qty, fo.unitPrice, fo.buySell)
        }
        .sequence
        .map { items =>
          makeOrder(ono, odt, ano, NonEmptyList.of(items.head, items.tail: _*))
        }
        .fold(_.invalid[Order], identity)
    }

    private[domain] def makeLineItem(
        isin: String,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        buySell: String
    ): ValidationResult[LineItem] = {
      (
        Instrument.validateISINCode(isin),
        validateQuantity(quantity),
        validateUnitPrice(unitPrice),
        validateBuySell(buySell)
      ).mapN { (isin, qty, price, bs) =>
        LineItem(isin, qty, price, BuySell.withName(bs))
      }
    }

    private[domain] def makeOrder(
        orderNo: String,
        orderDate: LocalDateTime,
        accountNo: String,
        lineItems: NonEmptyList[LineItem]
    ): ValidationResult[Order] = {
      (
        validateOrderNo(orderNo),
        Account.validateAccountNo(accountNo)
      ).mapN { (orderNo, accountNo) =>
        Order(
          orderNo,
          orderDate,
          accountNo,
          lineItems
        )
      }
    }

    private[model] def validateQuantity(
        qty: BigDecimal
    ): ValidationResult[Quantity] = {
      validate[Quantity](qty).toValidated
        .leftMap(_ :+ s"Quantity has to be positive: found $qty")
    }

    private[model] def validateUnitPrice(
        price: BigDecimal
    ): ValidationResult[UnitPrice] = {
      validate[UnitPrice](price).toValidated
        .leftMap(_ :+ s"Unit Price has to be positive: found $price")
    }

    private[model] def validateOrderNo(
        orderNo: String
    ): ValidationResult[OrderNo] = {
      validate[OrderNo](orderNo).toValidated
    }

    private[model] def validateBuySell(bs: String): ValidationResult[String] = {
      BuySell
        .withNameEither(bs)
        .toValidatedNec
        .map(_.entryName)
        .leftMap(_.map(_.toString))
    }

//     def main(): Unit = {
//       val o1 =
//         FrontOfficeOrder("a-1", Instant.now(), "isin-12345", 100.00, "B")
//       val o2 =
//         FrontOfficeOrder("a-1", Instant.now(), "isin-12346", 200.00, "S")
//       val o3 =
//         FrontOfficeOrder("a-2", Instant.now(), "isin-12345", 100.00, "B")
//       val orders = List(o1, o2, o3)
//
//       val csv = orders.writeComplete.print(Printer.default)
//       println(csv)
//       // accountNo,date,isin,qty,buySell
//       // a-1,2020-07-02T05:05:13.619Z,isin-12345,100.0,B
//       // a-1,2020-07-02T05:05:13.619Z,isin-12346,200.0,S
//       // a-2,2020-07-02T05:05:13.619Z,isin-12345,100.0,B
//
//       fromFrontOffice(csv) match {
//         case Left(e) => println(e)
//         case Right(v) => v.foreach(println)
//       }
//     }
  }
}

package tradex.domain
package model

import java.time.LocalDateTime
import java.time.Instant
import java.util.UUID

import cats.data.NonEmptyList
import cats.data.EitherNec
import cats.syntax.all._
import cats.instances.list._

import instrument._
import account._
import NewtypeRefinedOps._
import enumeratum._
import io.estatico.newtype.macros.newtype

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._

import derevo.cats._
import derevo.circe.magnolia._
import derevo.derive
import io.circe.refined._

object order {
  @derive(decoder, encoder, eqv, show)
  @newtype case class OrderNo(value: NonEmptyString)

  @derive(decoder, encoder, eqv, show)
  @newtype case class Quantity(value: BigDecimal Refined NonNegative)

  @derive(decoder, encoder, eqv, show)
  @newtype case class UnitPrice(value: BigDecimal Refined Positive)

  @derive(decoder, encoder, eqv, show)
  sealed abstract class BuySell(override val entryName: String)
      extends EnumEntry

  object BuySell extends Enum[BuySell] {
    case object Buy extends BuySell("buy")
    case object Sell extends BuySell("sell")

    val values = findValues
  }

  // domain entity
  private[domain] final case class LineItem private (
      instrument: ISINCode,
      quantity: Quantity,
      unitPrice: UnitPrice,
      buySell: BuySell
  )

  private[domain] final case class Order private (
      no: OrderNo,
      date: LocalDateTime,
      accountNo: AccountNo,
      items: NonEmptyList[LineItem]
  )

  private[domain] final case class FrontOfficeOrder private (
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
    ): EitherNec[String, List[Order]] = {
      frontOfficeOrders.toList
        .groupBy(_.accountNo)
        .map {
          case (ano, forders) =>
            makeOrder(UUID.randomUUID.toString, today, ano, forders)
        }
        .toList
        .sequence
    }

    private[domain] def makeOrder(
        ono: String,
        odt: LocalDateTime,
        ano: String,
        forders: List[FrontOfficeOrder]
    ): EitherNec[String, Order] = {
      forders
        .map { fo =>
          makeLineItem(fo.isin, fo.qty, fo.unitPrice, fo.buySell)
        }
        .sequence
        .map { items =>
          makeOrder(ono, odt, ano, NonEmptyList.of(items.head, items.tail: _*))
        }
        .fold(Left(_), identity)
    }

    private[domain] def makeLineItem(
        isin: String,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        buySell: String
    ): EitherNec[String, LineItem] = {
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
    ): EitherNec[String, Order] = {
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
    ): EitherNec[String, Quantity] = {
      validate[Quantity](qty)
        .leftMap(_ :+ s"Quantity has to be positive: found $qty")
    }

    private[model] def validateUnitPrice(
        price: BigDecimal
    ): EitherNec[String, UnitPrice] = {
      validate[UnitPrice](price)
        .leftMap(_ :+ s"Unit Price has to be positive: found $price")
    }

    private[model] def validateOrderNo(
        orderNo: String
    ): EitherNec[String, OrderNo] = {
      validate[OrderNo](orderNo)
    }

    private[model] def validateBuySell(
        bs: String
    ): EitherNec[String, String] = {
      BuySell
        .withNameEither(bs)
        .toEitherNec
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

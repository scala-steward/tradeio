package tradex.domain
package repository

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import natchez.Trace.Implicits.noop
import generators._
import repository.AccountRepository

import suite.ResourceSuite

object PostgresSuite extends ResourceSuite {

	val flushTables: List[Command[Void]] =
    List("users", "taxfees", "instruments", "accounts").map { table =>
      sql"DELETE FROM #$table".command
    }

  type Res = Resource[IO, Session[IO]]

  override def sharedResource: Resource[IO, Res] =
    Session
      .pooled[IO](
        host = "localhost",
        port = 5432,
        user = "postgres",
        password = None,
        database = "trading",
        max = 10,
        strategy = Strategy.SearchPath
      )
      .beforeAll {
        _.use { s =>
          flushTables.traverse_(s.execute)
        }
      }

	test("Accounts with no upsert") { postgres =>
    val a = AccountRepository.make[IO](postgres)
    forall(accountGen) { account =>
      for {
        x <- a.all
        _ <- a.store(account, upsert = false)
        y <- a.all
        z <- a.store(account, upsert = false).attempt
      } yield expect.all(x.isEmpty, y.count(_.no === account.no) === 1, z.isLeft)
    }
  }

	test("Accounts with upsert") { postgres =>
    val a = AccountRepository.make[IO](postgres)
    forall(accountGen) { account =>
      for {
        x <- a.all
        _ <- a.store(account)
        y <- a.all
        z <- a.store(account).attempt
      } yield expect.all(x.isEmpty, y.count(_.no === account.no) === 1, z.isRight)
    }
  }
}

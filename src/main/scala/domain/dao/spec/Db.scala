package at.energydash
package domain.dao.spec

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile


trait Db {

  val db: JdbcProfile#Backend#Database
  val config: DatabaseConfig[JdbcProfile]

}

object Db {
  def getConfig: DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig[JdbcProfile]("slick.pgsql.local")
  }
}

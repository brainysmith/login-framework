package com.identityblitz.lang.scala

/**
 *
 */
trait CustomEnumeration[A <: {def name: String}] {

  trait Val {
    self: A =>

    def apply() {
      _values += ((this.name, this))
    }
  }

  private var _values = Map.empty[String, A]

  /**
   * Return an iterator through the enumeration's values.
   * @return - an iterator through values.
   */
  def values = _values.values

  /**
   * Return an element that has a name given.
   * @param name - a name to search by.
   * @return - if there is an element with such a name then the method returns that element, otherwise throws NoSuchElementException.
   */
  def valueOf(name: String): A = _values(name)

  /**
   * Returns an Option of element that has a name given.
   * @param name - a name to search by.
   * @return - if there is an element with such a name then the method returns Some(that element), otherwise None.
   */
  def optValueOf(name: String): Option[A] = _values.get(name)

}

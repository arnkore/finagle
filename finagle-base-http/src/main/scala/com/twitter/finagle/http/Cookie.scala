package com.twitter.finagle.http

import com.twitter.conversions.time._
import com.twitter.util.Duration
import java.util.{BitSet => JBitSet}
import org.jboss.netty.handler.codec.http.{Cookie => NettyCookie}

object Cookie {

  private[finagle] val DefaultMaxAge = Int.MinValue.seconds // Netty's DefaultCookie default.

  private[this] val IllegalNameChars = Set('\t', '\n', '\u000b', '\f', '\r', ' ', ',', ';', '=')
  private[this] val IllegalValueChars = Set('\n', '\u000b', '\f', '\r', ';')

  private[this] val IllegalNameCharsBitSet: JBitSet = {
    val bs = new JBitSet
    IllegalNameChars.foreach(bs.set(_))
    bs
  }

  private[this] val IllegalValueCharsBitSet: JBitSet = {
    val bs = new JBitSet
    IllegalValueChars.foreach(bs.set(_))
    bs
  }

  private[http] def stringContains(string: String, chars: JBitSet): Boolean = {
    var i = 0
    val until = string.length
    while (i < until && !chars.get(string.charAt(i))) {
      i += 1
    }
    i != until
  }

  // These are the same checks made by Netty's DefaultCookie
  private def validateName(name: String): String = {
    val trimmed = name.trim
    if (trimmed.isEmpty) throw new IllegalArgumentException("Cookie name cannot be empty")
    else {
      if (trimmed.head == '$')
        throw new IllegalArgumentException(
          s"Cookie name starting with '$$' not allowed: $trimmed"
        )
      else if (stringContains(trimmed, IllegalNameCharsBitSet))
        throw new IllegalArgumentException(
          s"Cookie name contains one of the following prohibited characters: ${IllegalNameChars
            .mkString(",")}: $trimmed"
        )
      else
        trimmed
    }
  }

  // These are the same checks made by Netty's DefaultCookie
  private def validateField(field: String): String = {
    if (field == null) field
    else {
      val trimmed = field.trim
      if (trimmed.isEmpty) {
        null
      } else if (stringContains(trimmed, IllegalValueCharsBitSet))
        throw new IllegalArgumentException(
          s"Cookie field contains one of the following prohibited characters: ${IllegalValueChars
            .mkString(",")}: $trimmed"
        )
      else trimmed
    }
  }
}

// A small amount of naming hopscotch is needed while we deprecate the `set` methods in preparation
// for removal; the param names can't conflict with the `get` method names, hence the underscored
// param names.
//
// `_forDeprecation` is an unused param; it's needed so we can offer a unique constructor (below)
// that takes all fields with the proper names while we deprecate the `set` methods. Once we remove
// the `set` methods, we can clean this up.
class Cookie private (
  private[this] var _name: String,
  private[this] var _value: String,
  private[this] var _domain: String,
  private[this] var _path: String,
  private[this] var _maxAge: Option[Duration],
  private[this] var _secure: Boolean,
  private[this] var _httpOnly: Boolean,
  private[this] var _forDeprecation: Boolean
) {

  /**
   * Create a cookie.
   */
  def this(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Duration] = Some(Cookie.DefaultMaxAge),
    secure: Boolean = false,
    httpOnly: Boolean = false
  ) = this(
    _name = Cookie.validateName(name),
    _value = value,
    _domain = Cookie.validateField(domain.orNull),
    _path = Cookie.validateField(path.orNull),
    _maxAge = maxAge,
    _secure = secure,
    _httpOnly = httpOnly,
    _forDeprecation = true
  )

  def this(
    name: String,
    value: String
  ) = this(
    name,
    value,
    None,
    None,
    Some(Cookie.DefaultMaxAge),
    false,
    false
  )

  @deprecated("Use Bijections.from to create a Cookie from a Netty Cookie")
  def this(underlying: NettyCookie) = {
    this(
      underlying.getName,
      underlying.getValue,
      underlying.getDomain,
      underlying.getPath,
      Option(underlying.getMaxAge.seconds),
      underlying.isSecure,
      underlying.isHttpOnly,
      true
    )
  }

  /**
   * Get the domain.
   * @note May be null.
   */
  def domain: String = _domain

  /**
   * Get the path.
   * @note May be null.
   */
  def path: String = _path
  def name: String = _name
  def value: String = _value
  def maxAge: Duration = _maxAge match {
    case Some(maxAge) => maxAge
    case None => Cookie.DefaultMaxAge
  }
  def httpOnly: Boolean = _httpOnly
  def secure: Boolean = _secure

  /**
   * Set the domain.
   * @note `domain` may be null.
   */
  @deprecated("Set domain in the Cookie constructor or use `Cookie.domain`", "2017-08-16")
  def domain_=(domain: String): Unit =
    _domain = Cookie.validateField(domain)

  /**
   * Set the path.
   * @note `path` may be null.
   */
  @deprecated("Set path in the Cookie constructor or use `Cookie.path`", "2017-08-16")
  def path_=(path: String): Unit =
    _path = Cookie.validateField(path)

  /**
   * Set the value.
   * @note `value` must not be null.
   */
  @deprecated("Set value in the Cookie constructor", "2017-08-16")
  def value_=(value: String): Unit =
    _value = value

  // Helper method for `equals` that returns true if two strings are both null, or have the
  // same value (ignoring case)
  private[this] def stringsEqual(s0: String, s1: String): Boolean = {
    if (s0 == null) {
      if (s1 == null) true
      else false
    } else {
      if (s1 == null) false
      else s0.equalsIgnoreCase(s1)
    }
  }

  private[this] def copy(
    name: String = _name,
    value: String = _value,
    domain: Option[String] = Option(_domain),
    path: Option[String] = Option(_path),
    maxAge: Option[Duration] = _maxAge,
    secure: Boolean = _secure,
    httpOnly: Boolean = _httpOnly
  ): Cookie =
    new Cookie(
      name,
      value,
      domain,
      path,
      maxAge,
      secure,
      httpOnly
    )

  /**
   * Create a new [[Cookie]] with the same set fields, and domain `domain`.
   */
  def domain(domain: Option[String]): Cookie =
    copy(domain = domain)

  /**
   * Create a new [[Cookie]] with the same set fields, and maxAge `maxAge`
   */
  def maxAge(maxAge: Option[Duration]): Cookie =
    copy(maxAge = maxAge)

  /**
   * Create a new [[Cookie]] with the same set fields, and path `path`.
   */
  def path(path: Option[String]): Cookie =
    copy(path = path)

  /**
   * Create a new [[Cookie]] with the same set fields, and httpOnly `httpOnly`
   */
  def httpOnly(httpOnly: Boolean): Cookie =
    copy(httpOnly = httpOnly)

  /**
   * Create a new [[Cookie]] with the same set fields, and secure `secure`
   */
  def secure(secure: Boolean): Cookie =
    copy(secure = secure)

  /**
   * Returns true if `obj` equals `this`. Two cookies are considered equal if their names,
   * paths, and domains are the same (ignoring case). This mimics the `equals` method on Netty's
   * DefaultCookie.
   */
  override def equals(obj: Any): Boolean = obj match {
    case c: Cookie =>
      name.equalsIgnoreCase(c.name) && stringsEqual(path, c.path) && stringsEqual(domain, c.domain)
    case _ => false
  }

  /**
   * From Netty 3's DefaultCookie
   */
  override def hashCode(): Int =
    name.hashCode
}

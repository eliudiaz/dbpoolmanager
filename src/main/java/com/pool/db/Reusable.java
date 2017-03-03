
package com.pool.db;

/**
 * Interface for an object that can be reused.
 * 
 * @see snaq.util.ObjectPool
 * 
 * @author Giles Winstanley
 */
public interface Reusable
{
  /**
   * Cleans an object to put it in a state in which it can be reused.
   * @throws Exception if unable to recycle the this object
   */
  void recycle() throws Exception;

  /**
   * Determines if this object is &quot;dirty&quot; (i.e. unable to be recycled).
   * @return true if object is unable to be recycled, false otherwise
   */
  boolean isDirty();
}

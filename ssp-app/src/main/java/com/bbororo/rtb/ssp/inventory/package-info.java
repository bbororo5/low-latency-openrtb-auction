/**
 * Inventory serving boundary that maps provider placements to SSP auction settings.
 *
 * <p>The current in-memory implementation is a hot-path serving copy. A future
 * external inventory store can become the source of truth without changing the
 * auction components that depend on this boundary.</p>
 */
package com.bbororo.rtb.ssp.inventory;

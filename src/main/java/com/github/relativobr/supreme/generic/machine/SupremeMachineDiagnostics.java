package com.github.relativobr.supreme.generic.machine;

import java.util.List;
import org.bukkit.block.Block;

/**
 * Supplies authoritative live state for {@code /supreme doctor machine}.
 *
 * <p>Specialized Supreme machines keep their own processing state, so diagnostics must be provided
 * by the machine that owns that state instead of inferred from GenericMachine's default engine.</p>
 */
public interface SupremeMachineDiagnostics {

  List<String> getMachineDiagnosticLines(Block block);
}

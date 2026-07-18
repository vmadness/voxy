package me.cortex.voxy.common.config.section;

import me.cortex.voxy.common.config.IMappingStorage;
import me.cortex.voxy.common.config.IStoredSectionPositionIterator;
import me.cortex.voxy.common.world.WorldSection;

public abstract class SectionStorage implements IMappingStorage, IStoredSectionPositionIterator {
    public abstract int loadSection(WorldSection into);

    /** Exact storage membership check which must not instantiate a WorldSection. */
    public abstract boolean hasSection(long sectionKey);

    public abstract void saveSection(WorldSection section);
}

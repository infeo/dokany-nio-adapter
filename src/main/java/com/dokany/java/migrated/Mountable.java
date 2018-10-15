package com.dokany.java.migrated;

import com.dokany.java.migrated.structure.DokanOptions;
import com.dokany.java.structure.VolumeInformation;

import java.nio.file.Path;

/**
 * An object which can be mounted in a filesystem.
 *
 * @author Armin Schrnek
 * @since 2.0
 */
public interface Mountable {

    void mount(Path mountPoint, DokanOptions dokanOptions, VolumeInformation volumeInformation);

    void unmount();

}

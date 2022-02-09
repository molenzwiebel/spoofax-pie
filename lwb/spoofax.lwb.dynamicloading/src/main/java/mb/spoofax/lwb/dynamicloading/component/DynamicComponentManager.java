package mb.spoofax.lwb.dynamicloading.component;

import mb.common.option.Option;
import mb.common.util.CollectionView;
import mb.resource.hierarchical.ResourcePath;
import mb.spoofax.core.Coordinate;
import mb.spoofax.core.CoordinateRequirement;
import mb.spoofax.core.component.ComponentManager;
import mb.spoofax.lwb.dynamicloading.DynamicLoadException;

public interface DynamicComponentManager extends ComponentManager {
    Option<DynamicComponent> getDynamicComponent(ResourcePath rootDirectory);

    Option<DynamicComponent> getDynamicComponent(Coordinate coordinate);

    CollectionView<DynamicComponent> getDynamicComponents(CoordinateRequirement coordinateRequirement);

    default Option<DynamicComponent> getOneDynamicComponent(CoordinateRequirement coordinateRequirement) {
        final CollectionView<DynamicComponent> components = getDynamicComponents(coordinateRequirement);
        if(components.size() == 1) {
            return Option.ofSome(components.iterator().next());
        } else {
            return Option.ofNone();
        }
    }

    Option<DynamicComponent> getDynamicComponent(String fileExtension);


    void registerListener(DynamicComponentManagerListener listener);

    void unregisterListener(DynamicComponentManagerListener listener);


    DynamicComponent loadOrReloadFromCompiledSources(
        ResourcePath rootDirectory,
        Iterable<ResourcePath> javaClassPaths,
        String participantClassQualifiedId
    ) throws DynamicLoadException;

    void unloadFromCompiledSources(ResourcePath rootDirectory);


    @Override void close();
}

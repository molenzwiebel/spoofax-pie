package mb.spoofax.lwb.dynamicloading.component;

import mb.common.option.Option;
import mb.common.util.CollectionView;
import mb.common.util.ListView;
import mb.common.util.MapView;
import mb.common.util.SetView;
import mb.log.api.Logger;
import mb.log.dagger.LoggerComponent;
import mb.pie.runtime.store.SerializingStoreBuilder;
import mb.pie.runtime.store.SerializingStoreInMemoryBuffer;
import mb.resource.ResourceService;
import mb.resource.dagger.ResourceServiceComponent;
import mb.resource.hierarchical.ResourcePath;
import mb.spoofax.core.Coordinate;
import mb.spoofax.core.CoordinateRequirement;
import mb.spoofax.core.component.Component;
import mb.spoofax.core.component.ComponentBuilderBase;
import mb.spoofax.core.component.ComponentGroupImpl;
import mb.spoofax.core.component.Participant;
import mb.spoofax.core.component.ComponentImpl;
import mb.spoofax.core.component.StaticComponentManager;
import mb.spoofax.core.language.LanguageComponent;
import mb.spoofax.core.platform.PlatformComponent;
import mb.spoofax.lwb.dynamicloading.DynamicLoadException;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public class DynamicComponentManagerImpl<L extends LoggerComponent, R extends ResourceServiceComponent, P extends PlatformComponent> extends ComponentBuilderBase<L, R, P> implements DynamicComponentManager {
    private final Logger logger;
    private final StaticComponentManager<L, R, P> staticComponentManager;

    private final HashMap<String, DynamicComponent> dynamicComponentPerFileExtension = new HashMap<>();
    private final HashMap<Coordinate, DynamicComponent> dynamicComponentPerCoordinate = new HashMap<>();
    private final HashMap<ResourcePath, DynamicComponent> dynamicComponentPerCompiledSources = new HashMap<>();
    private final Set<DynamicComponentManagerListener> listeners = new LinkedHashSet<>();

    public DynamicComponentManagerImpl(
        StaticComponentManager<L, R, P> staticComponentManager
    ) {
        super(
            staticComponentManager.loggerComponent,
            staticComponentManager.baseResourceServiceComponent,
            staticComponentManager.platformComponent,
            staticComponentManager.pieBuilderSupplier
        );
        this.logger = staticComponentManager.loggerComponent.getLoggerFactory().create(getClass());
        this.staticComponentManager = staticComponentManager;
    }

    @Override public void close() {
        listeners.clear();
        final ArrayList<Exception> exceptions = new ArrayList<>();
        dynamicComponentPerCompiledSources.forEach((rootDirectory, language) -> {
            try {
                language.close();
            } catch(IOException e) {
                exceptions.add(e);
            }
        });
        dynamicComponentPerCompiledSources.clear();
        dynamicComponentPerCoordinate.clear();
        dynamicComponentPerFileExtension.clear();
        staticComponentManager.close();
        if(exceptions.size() > 0) {
            final RuntimeException exception = new RuntimeException("Closing one or more dynamically loaded components failed; resources may have been leaked");
            exceptions.forEach(exception::addSuppressed);
            throw exception;
        }
    }


    @Override
    public LoggerComponent getLoggerComponent() {
        return loggerComponent;
    }

    @Override
    public PlatformComponent getPlatformComponent() {
        return platformComponent;
    }

    @Override
    public Option<? extends Component> getComponent(Coordinate coordinate) {
        return getDynamicComponent(coordinate)
            .mapOrElse(Option::ofSome, () -> staticComponentManager.getComponent(coordinate));
    }

    @Override
    public CollectionView<Component> getComponents(CoordinateRequirement coordinateRequirement) {
        final CollectionView<DynamicComponent> dynamicComponents = getDynamicComponents(coordinateRequirement);
        final CollectionView<Component> staticComponents = staticComponentManager.getComponents(coordinateRequirement);
        return CollectionView.of(Stream.concat(dynamicComponents.stream(), staticComponents.stream()));
    }

    @Override
    public Option<ComponentImpl<?, ?, ?>> getStandaloneComponent(Coordinate coordinate) {
        return staticComponentManager.getStandaloneComponent(coordinate);
    }

    @Override
    public MapView<Coordinate, ? extends ComponentImpl<?, ?, ?>> getStandaloneComponents() {
        return staticComponentManager.getStandaloneComponents();
    }

    @Override
    public Option<ComponentGroupImpl<?, ?, ?>> getComponentGroup(String group) {
        return staticComponentManager.getComponentGroup(group);
    }

    @Override
    public MapView<String, ? extends ComponentGroupImpl<?, ?, ?>> getComponentGroups() {
        return staticComponentManager.getComponentGroups();
    }

    @Override
    public Option<LanguageComponent> getLanguageComponent(Coordinate coordinate) {
        return Option.ofNullable(dynamicComponentPerCoordinate.get(coordinate))
            .flatMap(DynamicComponent::getLanguageComponent)
            .orElse(() -> staticComponentManager.getLanguageComponent(coordinate));
    }

    @Override
    public CollectionView<LanguageComponent> getLanguageComponents(CoordinateRequirement coordinateRequirement) {
        final Stream<LanguageComponent> dynamicComponents = dynamicComponentPerCompiledSources.values().stream()
            .flatMap(dc -> dc.getLanguageComponents(coordinateRequirement).stream());
        final CollectionView<LanguageComponent> staticComponents = staticComponentManager.getLanguageComponents(coordinateRequirement);
        return CollectionView.of(Stream.concat(dynamicComponents, staticComponents.stream()));
    }


    @Override
    public Option<DynamicComponent> getDynamicComponent(ResourcePath rootDirectory) {
        return Option.ofNullable(dynamicComponentPerCompiledSources.get(rootDirectory));
    }

    @Override
    public Option<DynamicComponent> getDynamicComponent(Coordinate coordinate) {
        return Option.ofNullable(dynamicComponentPerCoordinate.get(coordinate));
    }

    @Override
    public CollectionView<DynamicComponent> getDynamicComponents(CoordinateRequirement coordinateRequirement) {
        return CollectionView.of(dynamicComponentPerCompiledSources.values().stream().filter(dc -> coordinateRequirement.matches(dc.getInfo().coordinate)));
    }

    @Override
    public Option<DynamicComponent> getDynamicComponent(String fileExtension) {
        return Option.ofNullable(dynamicComponentPerFileExtension.get(fileExtension));
    }


    @Override public DynamicComponent loadOrReloadFromCompiledSources(
        ResourcePath rootDirectory,
        Iterable<ResourcePath> javaClassPaths,
        String participantClassQualifiedId
    ) throws DynamicLoadException {
        // Create classloader
        final URLClassLoader classLoader;
        try {
            classLoader = new URLClassLoader(classPathToUrl(javaClassPaths, baseResourceServiceComponent.getResourceService()), getClass().getClassLoader());
        } catch(MalformedURLException e) {
            throw DynamicLoadException.classPathToUrlFail(e);
        }

        // Get participant instance.
        final Participant<L, R, P> participant;
        try {
            final Class<?> participantClass = classLoader.loadClass(participantClassQualifiedId);
            // noinspection unchecked (cast is safe due to checks in the next code block)
            participant = (Participant<L, R, P>)participantClass.getDeclaredConstructor().newInstance();
        } catch(ReflectiveOperationException e) {
            throw DynamicLoadException.participantInstantiateFail(e);
        }

        // Check whether the participant is compatible with our classes, otherwise we cannot use `Participant<L, R, P>`
        final Class<? super L> requiredLoggerComponentClass = participant.getRequiredLoggerComponentClass();
        final Class<? extends LoggerComponent> loggerComponentClass = staticComponentManager.loggerComponent.getClass();
        if(!requiredLoggerComponentClass.isAssignableFrom(loggerComponentClass)) {
            throw DynamicLoadException.incompatibleLoggerComponent(requiredLoggerComponentClass.getName(), loggerComponentClass.getName());
        }
        final Class<? super R> requiredBaseResourceServiceComponentClass = participant.getRequiredBaseResourceServiceComponentClass();
        final Class<? extends ResourceServiceComponent> baseResourceServiceComponentClass = staticComponentManager.baseResourceServiceComponent.getClass();
        if(!requiredBaseResourceServiceComponentClass.isAssignableFrom(baseResourceServiceComponentClass)) {
            throw DynamicLoadException.incompatibleBaseResourceServiceComponent(requiredBaseResourceServiceComponentClass.getName(), baseResourceServiceComponentClass.getName());
        }
        final Class<? super P> requiredPlatformComponentClass = participant.getRequiredPlatformComponentClass();
        final Class<? extends PlatformComponent> platformComponentClass = staticComponentManager.platformComponent.getClass();
        if(!requiredPlatformComponentClass.isAssignableFrom(platformComponentClass)) {
            throw DynamicLoadException.incompatiblePlatformComponent(requiredPlatformComponentClass.getName(), platformComponentClass.getName());
        }

        // Check for overlapping coordinates or file extensions. TODO: check with static components as well?
        final Coordinate coordinate = participant.getCoordinate();
        final @Nullable DynamicComponent componentForCoordinates = dynamicComponentPerCoordinate.get(coordinate);
        if(componentForCoordinates != null && !componentForCoordinates.getInfo().rootDirectory.equals(rootDirectory)) {
            throw DynamicLoadException.duplicateCoordinateFail(coordinate, componentForCoordinates.getInfo());
        }
        for(String fileExtension : participant.getLanguageFileExtensions()) {
            final @Nullable DynamicComponent componentForExtension = dynamicComponentPerFileExtension.get(fileExtension);
            if(componentForExtension != null && !componentForExtension.getInfo().rootDirectory.equals(rootDirectory)) {
                throw DynamicLoadException.duplicateFileExtensionFail(fileExtension, componentForExtension.getInfo());
            }
        }

        final @Nullable DynamicComponent previousComponent = dynamicComponentPerCompiledSources.get(rootDirectory);
        final SerializingStoreInMemoryBuffer serializingStoreInMemoryBuffer;
        if(previousComponent != null) {
            // Get the PIE store buffer for the existing dynamic component.
            serializingStoreInMemoryBuffer = previousComponent.serializingStoreInMemoryBuffer;
            // Close it to serialize its state to the buffer, and then pass that along to be deserialized later. A
            // serialize-deserialize roundtrip is required because we are closing the classloader of the dynamic
            // component, and thus may not keep any instances of classes of the component around, as this prevents the
            // classes from being garbage collected.
            try {
                previousComponent.close();
            } catch(IOException e) {
                throw DynamicLoadException.closeExistingFail(e);
            }
        } else { // New dynamic component, so just create a new buffer.
            serializingStoreInMemoryBuffer = new SerializingStoreInMemoryBuffer();
        }

        final BuildOneResult result = super.buildOne( // NOTE: only support dynamically loading a single standalone component right now.
            participant,
            staticComponentManager.globalResourceRegistryProviders,
            staticComponentManager.resourceServiceModuleCustomizers,
            staticComponentManager.globalTaskDefsProviders,
            ListView.of(Stream.concat(staticComponentManager.pieModuleCustomizers.stream(), Stream.of(pieModule -> pieModule
                .withStoreFactory((serde, resourceService, loggerFactory) -> SerializingStoreBuilder.ofInMemoryStore(serde)
                    .withInMemoryBuffer(serializingStoreInMemoryBuffer) // Pass in buffer for deserialization.
                    .withLoggingDeserializeFailHandler(loggerFactory)
                    .build()
                )))),
            classLoader
        ); // NOTE: global providers are ignored, as it would require all participants to be reconstructed.
        final ComponentImpl<L, R, P> component = result.component;
        serializingStoreInMemoryBuffer.close(); // TODO: can we close this here? Should be fine since we do not use the buffer after deserialization?
        final DynamicComponent dynamicComponent = new DynamicComponent(rootDirectory, component.coordinate, classLoader, component, serializingStoreInMemoryBuffer);
        return registerComponent(rootDirectory, dynamicComponent);
    }

    @Override public void unloadFromCompiledSources(ResourcePath rootDirectory) {
        final @Nullable DynamicComponent component = dynamicComponentPerCompiledSources.remove(rootDirectory);
        if(component != null) {
            final Set<String> removedFileExtensions = unregisterComponent(component);
            notifyUnload(component, SetView.of(removedFileExtensions));
        }
    }


    @Override public void registerListener(DynamicComponentManagerListener listener) {
        listeners.add(listener);
    }

    @Override public void unregisterListener(DynamicComponentManagerListener listener) {
        listeners.remove(listener);
    }


    private DynamicComponent registerComponent(ResourcePath rootDirectory, DynamicComponent component) {
        final DynamicComponentInfo info = component.getInfo();
        final Coordinate coordinate = info.coordinate;

        final @Nullable DynamicComponent previousComponent = dynamicComponentPerCompiledSources.put(rootDirectory, component);
        final Set<String> previousFileExtensions;
        if(previousComponent != null) {
            previousFileExtensions = unregisterComponent(previousComponent);
        } else {
            previousFileExtensions = Collections.emptySet();
        }
        dynamicComponentPerCoordinate.put(coordinate, component);

        final Set<String> newFileExtensions = info.fileExtensions.asCopy();
        final Set<String> removedFileExtensions = new LinkedHashSet<>(previousFileExtensions);
        removedFileExtensions.removeAll(newFileExtensions);
        final Set<String> addedFileExtensions = new LinkedHashSet<>(newFileExtensions);
        newFileExtensions.removeAll(previousFileExtensions);
        for(String fileExtension : addedFileExtensions) {
            dynamicComponentPerFileExtension.put(fileExtension, component);
        }

        if(previousComponent != null && previousComponent.getInfo().coordinate.equals(coordinate)) {
            notifyReload(previousComponent, component, SetView.of(removedFileExtensions), SetView.of(addedFileExtensions));
        } else {
            notifyLoad(component, SetView.of(addedFileExtensions));
        }

        return component;
    }

    private Set<String> unregisterComponent(DynamicComponent component) {
        final DynamicComponentInfo info = component.getInfo();
        final Coordinate coordinate = info.coordinate;
        final Set<String> removedFileExtensions = new LinkedHashSet<>();
        dynamicComponentPerCoordinate.remove(coordinate);
        for(String extension : info.fileExtensions) {
            if(dynamicComponentPerFileExtension.remove(extension, component)) {
                removedFileExtensions.add(extension);
            }
        }
        try {
            component.close();
        } catch(IOException e) {
            logger.error("Failed to close dynamically loaded component '{}'; resources may have been leaked", e, component);
        }
        return removedFileExtensions;
    }

    private void notifyLoad(DynamicComponent component, SetView<String> addedFileExtensions) {
        listeners.forEach(l -> l.load(component, addedFileExtensions));
    }

    private void notifyReload(DynamicComponent previousComponent, DynamicComponent component, SetView<String> removedFileExtensions, SetView<String> addedFileExtensions) {
        listeners.forEach(l -> l.reload(previousComponent, component, removedFileExtensions, addedFileExtensions));
    }

    private void notifyUnload(DynamicComponent component, SetView<String> removedFileExtensions) {
        listeners.forEach(l -> l.unload(component, removedFileExtensions));
    }

    private static URL[] classPathToUrl(Iterable<ResourcePath> classPath, ResourceService resourceService) throws MalformedURLException {
        final ArrayList<URL> classPathUrls = new ArrayList<>();
        for(ResourcePath path : classPath) {
            final @Nullable File file = resourceService.toLocalFile(path);
            if(file == null) {
                throw new MalformedURLException("Cannot dynamically load language; resource at path '" + path + "' is not on the local filesystem, and can therefore not be loaded into a URLClassLoader");
            }
            classPathUrls.add(file.toURI().toURL());
        }
        return classPathUrls.toArray(new URL[0]);
    }
}

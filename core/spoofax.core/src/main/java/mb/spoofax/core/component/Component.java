package mb.spoofax.core.component;

import mb.common.option.Option;
import mb.pie.dagger.PieComponent;
import mb.resource.dagger.ResourceRegistriesProvider;
import mb.resource.dagger.ResourceServiceComponent;
import mb.spoofax.core.Coordinate;
import mb.spoofax.core.CoordinateRequirement;
import mb.spoofax.core.language.LanguageComponent;

public interface Component {
    Coordinate getCoordinate();

    default boolean matchesCoordinate(CoordinateRequirement coordinateRequirement) {
        return coordinateRequirement.matches(getCoordinate());
    }


    ResourceServiceComponent getResourceServiceComponent();

    Option<LanguageComponent> getLanguageComponent();

    PieComponent getPieComponent();

    <T> Option<T> getSubcomponent(Class<T> subcomponentType);
}

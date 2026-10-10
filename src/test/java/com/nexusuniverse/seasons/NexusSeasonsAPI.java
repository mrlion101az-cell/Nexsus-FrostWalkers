package com.nexusuniverse.seasons;

/** Test copy of NexusSeasons' public API (the bits NexusRime reads). */
public interface NexusSeasonsAPI {
    Season getCurrentSeason();

    int getCurrentYear();

    int getDayOfSeason();

    int getDaysPerSeason();
}

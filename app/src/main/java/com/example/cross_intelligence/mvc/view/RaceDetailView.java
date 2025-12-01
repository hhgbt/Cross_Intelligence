package com.example.cross_intelligence.mvc.view;

import com.example.cross_intelligence.mvc.model.Race;

public interface RaceDetailView {

    void renderRace(Race race);

    void onRaceUpdated();

    void onRaceUpdateFailed(Throwable throwable);
}






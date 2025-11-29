package com.example.cross_intelligence.mvc.view;

import com.example.cross_intelligence.mvc.model.Race;

import java.util.List;

/**
 * 赛事列表 View 层接口，由 Activity/Fragment 实现。
 */
public interface RaceListView {

    void showLoading();

    void showRaces(List<Race> races);

    void showEmpty();

    void showError(Throwable throwable);
}





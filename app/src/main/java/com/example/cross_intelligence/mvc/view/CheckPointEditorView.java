package com.example.cross_intelligence.mvc.view;

import com.example.cross_intelligence.mvc.model.CheckPoint;

import java.util.List;

public interface CheckPointEditorView {

    void displayCheckPoints(List<CheckPoint> checkPoints);

    void onCheckPointSaved();

    void onCheckPointDeleted();

    void onOperationFailed(Throwable throwable);
}







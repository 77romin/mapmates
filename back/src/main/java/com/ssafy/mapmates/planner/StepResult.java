package com.ssafy.mapmates.planner;

/** Tool 한 단계의 실행 결과(Resource). 다음 단계의 입력과 최종 Prompt 에 쓰인다. */
public record StepResult(String tool, String input, Object output) {}

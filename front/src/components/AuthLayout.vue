<script setup>
/** 로그인·회원가입·비밀번호 찾기 공통 배치: 넓은 화면에서는 왼쪽에 로고 그림, 오른쪽에 입력 폼 */
</script>

<template>
  <div class="auth-split">
    <aside class="auth-brand">
      <img src="/logo.webp" alt="너랑 갈.지도, 너랑 갈 다음 여행을 지도 위에" />
    </aside>
    <div class="auth-main">
      <div class="auth-box">
        <img src="/logo-wordmark.png" alt="너랑 갈.지도" class="auth-mark" />
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-split { min-height: calc(100vh - var(--mm-nav-h)); display: grid; grid-template-columns: 1fr; }
.auth-brand { display: none; }
.auth-main { display: flex; align-items: center; justify-content: center; padding: var(--mm-sp-6) var(--mm-sp-4); }
.auth-box { width: 100%; max-width: 380px; }
.auth-mark { height: 34px; width: auto; display: block; margin-bottom: var(--mm-sp-5); }
@media (min-width: 992px) {
  .auth-split { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  /* 로고 그림의 바탕색(--mm-paper)을 그대로 이어 붙여 그림이 화면의 일부처럼 보이게 한다 */
  .auth-brand { display: flex; align-items: center; justify-content: center; background: var(--mm-paper); padding: var(--mm-sp-6); }
  .auth-brand img { width: min(100%, 520px); height: auto; }
  .auth-mark { display: none; }
}
</style>

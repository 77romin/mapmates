<script setup>
import NavBar from '@/components/NavBar.vue'
import ChatWidget from '@/components/ChatWidget.vue'
</script>

<template>
  <NavBar />
  <main>
    <RouterView />
  </main>
  <ChatWidget />
</template>

<style scoped>
main { padding-top: var(--mm-nav-h); min-height: 100%; }
</style>

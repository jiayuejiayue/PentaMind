<script setup>
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const { params } = route
const { path } = params
router.replace('/' + (Array.isArray(path) ? path.join('/') : path))
</script>

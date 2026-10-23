(function () {
  var layers = [document.getElementById('layer-a'), document.getElementById('layer-b')]
  var screen = document.getElementById('screen')
  var pairingCode = document.getElementById('pairing-code')
  var pairingServer = document.getElementById('pairing-server')
  var statusTitle = document.getElementById('status-title')
  var statusDetail = document.getElementById('status-detail')
  var statusRetry = document.getElementById('status-retry')
  var logoImage = document.getElementById('logo')
  var logos = {
    CEFA: { src: '/brand/cefa-logo-white.svg', alt: 'CEFA' },
    ALGONTEC: { src: '/brand/algontec-logo-white.svg', alt: 'Algontec' }
  }
  var idleLogo = 'CEFA'
  var volume = 1
  Object.keys(logos).forEach(function (key) {
    new Image().src = logos[key].src
  })
  var current = -1
  var lastSeq = 0
  var retryAt = null
  var retryTimer = null

  function hostOf(url) {
    try {
      return new URL(url).host
    } catch (e) {
      return url || ''
    }
  }

  function renderRetry() {
    if (!retryAt) {
      statusRetry.textContent = ''
      return
    }
    var seconds = Math.max(0, Math.round((retryAt - Date.now()) / 1000))
    statusRetry.textContent = 'Próximo intento en ' + seconds + ' s'
  }

  function startRetryTimer(value) {
    retryAt = value || null
    if (retryTimer) {
      clearInterval(retryTimer)
      retryTimer = null
    }
    renderRetry()
    if (retryAt) {
      retryTimer = setInterval(renderRetry, 1000)
    }
  }

  function normalizeLogo(name) {
    var key = String(name || '').toUpperCase()
    return logos[key] ? key : 'CEFA'
  }

  function renderLogo(kind) {
    var logo = logos[kind === 'LOGO' ? idleLogo : 'CEFA']
    if (logoImage.getAttribute('src') !== logo.src) {
      logoImage.setAttribute('src', logo.src)
      logoImage.setAttribute('alt', logo.alt)
    }
  }

  function setVolume(level) {
    var parsed = Number(level)
    volume = isNaN(parsed) ? 1 : Math.max(0, Math.min(100, parsed)) / 100
    layers.forEach(function (layer) {
      layer.querySelectorAll('video').forEach(function (video) {
        video.volume = volume
      })
    })
  }

  function loadVolume() {
    fetch('/player/volume', { cache: 'no-store' })
      .then(function (response) {
        return response.json()
      })
      .then(function (body) {
        setVolume(body.volume)
      })
      .catch(function () {})
  }

  function showScreen(event) {
    var kind = event.screen || 'LOGO'
    if (event.logo) {
      idleLogo = normalizeLogo(event.logo)
    }
    screen.setAttribute('data-screen', kind)
    renderLogo(kind)
    if (kind === 'PAIRING') {
      pairingCode.textContent = event.code || '------'
      pairingServer.textContent = event.server ? 'Servidor: ' + hostOf(event.server) : ''
      startRetryTimer(null)
    } else if (kind === 'SEARCHING') {
      statusTitle.textContent = 'Buscando el servidor de CEFA TV'
      statusDetail.textContent = event.error || event.message || ''
      startRetryTimer(event.retryAt)
    } else if (kind === 'CONNECTING') {
      statusTitle.textContent = 'Conectando con el servidor'
      statusDetail.textContent = [event.server ? hostOf(event.server) : '', event.error || '']
        .filter(Boolean)
        .join(' · ')
      startRetryTimer(event.retryAt)
    } else {
      startRetryTimer(null)
    }
    screen.classList.add('visible')
    layers.forEach(function (layer) {
      layer.classList.remove('active')
      layer.innerHTML = ''
    })
    current = -1
  }

  function swapTo(index, previous) {
    screen.classList.remove('visible')
    layers[index].classList.add('active')
    if (previous >= 0 && previous !== index) {
      var old = layers[previous]
      old.classList.remove('active')
      setTimeout(function () {
        if (!old.classList.contains('active')) {
          old.innerHTML = ''
        }
      }, 600)
    }
    current = index
  }

  function show(event) {
    var previous = current
    var index = previous === 0 ? 1 : 0
    var layer = layers[index]
    layer.className = 'layer ' + (event.fit === 'CONTAIN' ? 'contain' : 'cover')
    layer.innerHTML = ''
    var finished = false
    function ready() {
      if (finished || event.seq < lastSeq) {
        return
      }
      finished = true
      swapTo(index, previous)
    }
    if (event.kind === 'VIDEO') {
      var video = document.createElement('video')
      video.src = event.src
      video.muted = event.muted !== false
      video.volume = volume
      video.loop = true
      video.autoplay = true
      video.playsInline = true
      video.addEventListener('playing', ready, { once: true })
      video.addEventListener('error', ready, { once: true })
      layer.appendChild(video)
      video.play().catch(function () {})
    } else {
      var image = new Image()
      image.addEventListener('load', ready, { once: true })
      image.addEventListener('error', ready, { once: true })
      image.src = event.src
      layer.appendChild(image)
    }
  }

  function handle(event) {
    if (event.type === 'SHOW') {
      lastSeq = event.seq
      show(event)
    } else if (event.type === 'SCREEN') {
      showScreen(event)
    } else if (event.type === 'CONFIG') {
      if ('idleLogo' in event) {
        idleLogo = normalizeLogo(event.idleLogo)
        renderLogo(screen.getAttribute('data-screen'))
      }
      if ('volume' in event) {
        setVolume(event.volume)
      }
    }
  }

  function connect() {
    var source = new EventSource('/player/events')
    source.onopen = loadVolume
    source.onmessage = function (message) {
      try {
        handle(JSON.parse(message.data))
      } catch (e) {
        console.error(e)
      }
    }
    source.onerror = function () {
      source.close()
      setTimeout(connect, 2000)
    }
  }

  connect()
})()

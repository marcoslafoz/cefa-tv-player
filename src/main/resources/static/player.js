(function () {
  var layers = [document.getElementById('layer-a'), document.getElementById('layer-b')]
  var idle = document.getElementById('idle')
  var idleMessage = document.getElementById('idle-message')
  var current = -1
  var lastSeq = 0

  function showIdle(message) {
    idleMessage.textContent = message || ''
    idle.classList.add('visible')
    layers.forEach(function (layer) {
      layer.classList.remove('active')
      layer.innerHTML = ''
    })
    current = -1
  }

  function swapTo(index, previous) {
    idle.classList.remove('visible')
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
    } else if (event.type === 'IDLE') {
      showIdle(event.message)
    }
  }

  function connect() {
    var source = new EventSource('/player/events')
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

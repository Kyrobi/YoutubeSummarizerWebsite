<script>
  let youtubeLink = $state("")
  let result = $state("")
  let loading = $state(false) // Keep track if the backend server is still processing the request

  async function submit(size) {
    
    loading = true

    try{
      // const response = await fetch(
      //   `http://localhost:8080/summarize?youtubeLink=${encodeURIComponent(youtubeLink)}&size=${encodeURIComponent(size)}`
      // )
      const response = await fetch(
        `https://videosummary-api.kyrobi.net/summarize?youtubeLink=${encodeURIComponent(youtubeLink)}&size=${encodeURIComponent(size)}`
      )


      const data = await response.json()
      result = data.summary

    } finally {
      loading = false
    }
  
  }

</script>

<main>
  <h1>YouTube Video Summarizer</h1>

  <input type="text" bind:value={youtubeLink} placeholder="Paste link here" />

  <p>Place Your Order</p>

  <div class="button-row">
    <button onclick={() => submit("L")} title="All the key points with context to understand the full picture.">🍔 Dave's Single<br>(Full Context)</button>
    <button onclick={() => submit("M")} title="Just the essentials. The main takeaways and critical points only.">🍗 10pc Nuggets<br>(Key Points)</button>
    <button onclick={() => submit("S")} title="The single most important takeaway in a sentence or two.">🍟 Medium Fries<br>(Couple Sentences)</button>
  </div>

  {#if loading}

    <p>⏳ Waiting on your order...</p>

  {:else if result}
    <div class="result-box">
      <p class="result-text">{result}</p>
    </div>
  {/if}

</main>
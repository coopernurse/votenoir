/*!
 * SaaS II WordPress theme. Twitter script.
 *
 * @category    SaaS_II_Theme
 * @package     js
 * @copyright   Copyright (c) 2010 Worry Free Labs, LLC. (http://worryfreelabs.com/)
 * @author      Oleksandr Bernatskyi
 */

;function twitterCallback(response)
{
	var $twitter = jQuery('div.tweets > div:first span.tweet');
	var viewMoreText = $twitter.find('a.view-more').html();
	
	$twitter.empty();
	
	jQuery(response).each
	(
		function(index, tweet)
		{
			var status = tweet.text
				.replace(/((https?|s?ftp|ssh)\:\/\/[^"\s\<\>]*[^.,;'">\:\s\<\>\)\]\!])/g, function(url) {
					return '<a href="' + url + '">' + url + '</a>';
				})
				.replace(/\B@([_a-z0-9]+)/ig, function(reply) {
					return reply.charAt(0) + '<a href="http://twitter.com/' + reply.substring(1) + '">' + reply.substring(1) + '</a>';
				});
			
			var $viewMore = jQuery('<a></a>')
				.attr('href', 'http://twitter.com/' + tweet.user.screen_name + '/statuses/' + tweet.id)
				.html(viewMoreText);
			
			$twitter
				.append(status)
				.append(' ')
				.append($viewMore)
				.find('a')
					.addClass('twitter-link')
					.attr('target', '_blank');
		}
	);
}

jQuery
(
	function($)
	{
		var $twitter = $('div.tweets > div:first');
		
		if (!$twitter.length)
		{
			return;
		}
		
		var $twitterTextContainer = $twitter.find('span:first');
		var twitterAccount = $twitter.find('strong').attr('class');
		var twitterCookieName = 'saas2-twitter-cache';
		var twitterHtml = $.cookie(twitterCookieName);
		
		if (twitterHtml)
		{
			$twitterTextContainer.html(twitterHtml);
		}
		else
		{
			$.getScript
			(
				'http://twitter.com/statuses/user_timeline/' + twitterAccount + '.json?callback=twitterCallback&count=1',
				function()
				{
					var text = $twitterTextContainer.html();
					
					if (text)
					{
						var cacheExpiration = new Date();
						cacheExpiration.setTime(cacheExpiration.getTime() + 60 * 60 * 1000);
						
						$.cookie(twitterCookieName, text, {expires: cacheExpiration, path: '/'});
					}
				}
			);
		}
	}
);
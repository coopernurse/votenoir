/*!
 * SaaS II WordPress theme
 *
 * @category    SaaS_II_Theme
 * @package     js
 * @copyright   Copyright (c) 2010 Worry Free Labs, LLC. (http://worryfreelabs.com/)
 * @author      Oleksandr Bernatskyi
 */

;jQuery
(
	function($)
	{
		/**
		 * Homepage
		 */
		// Slider
		$('.slider > div').easySlider();
		
		// Features
		$('div.features3 li:nth-child(3n-2)').addClass('first');
		
		
		/**
		 * Sidebar
		 */
		$('#searchform').parent('div').addClass('searchform');
		
		
		/**
		 * Pricing Grid
		 */
		var $gridSections = $('div.grid div.sections section');
		
		$gridSections.hover
		(
			function()
			{
				$gridSections.removeClass('on');
				$(this).addClass('on');
			}
		);
	}
);